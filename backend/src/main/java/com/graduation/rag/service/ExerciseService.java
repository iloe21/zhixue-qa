package com.graduation.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graduation.rag.dto.ExerciseDetail;
import com.graduation.rag.dto.GenerateExerciseRequest;
import com.graduation.rag.dto.SubmitExerciseRequest;
import com.graduation.rag.entity.ChunkMeta;
import com.graduation.rag.entity.Exercise;
import com.graduation.rag.entity.ExerciseQuestion;
import com.graduation.rag.entity.KnowledgeBase;
import com.graduation.rag.exception.BusinessException;
import com.graduation.rag.mapper.ChunkMetaMapper;
import com.graduation.rag.mapper.ExerciseMapper;
import com.graduation.rag.mapper.ExerciseQuestionMapper;
import com.graduation.rag.security.LoginUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 练习服务（设计文档 2.3 流程三 + 5.5）
 *
 * 出题：抽样知识库片段 → 提示词要求严格 JSON → qwen-plus 生成 → 解析入库（失败自动重试一次）
 * 判分：服务端比对（选择题精确匹配；填空题多答案 + 全半角归一化 + 忽略大小写）
 * 防作弊：待作答状态接口不返回 answer/explanation；提交仅限一次
 */
@Service
public class ExerciseService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseService.class);
    private static final int SCORE_PER_QUESTION = 5;

    private final ExerciseMapper exerciseMapper;
    private final ExerciseQuestionMapper questionMapper;
    private final ChunkMetaMapper chunkMetaMapper;
    private final KnowledgeBaseService kbService;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public ExerciseService(ExerciseMapper exerciseMapper, ExerciseQuestionMapper questionMapper,
                           ChunkMetaMapper chunkMetaMapper, KnowledgeBaseService kbService,
                           ChatModel chatModel, ObjectMapper objectMapper) {
        this.exerciseMapper = exerciseMapper;
        this.questionMapper = questionMapper;
        this.chunkMetaMapper = chunkMetaMapper;
        this.kbService = kbService;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    // ==================== 出题 ====================

    public ExerciseDetail generate(LoginUser user, GenerateExerciseRequest req) {
        KnowledgeBase kb = kbService.getForRead(req.getKbId(), user);
        int choiceCount = req.getChoiceCount() == null ? 5 : req.getChoiceCount();
        int blankCount = req.getBlankCount() == null ? 3 : req.getBlankCount();
        if (choiceCount < 1 || choiceCount > 10 || blankCount < 0 || blankCount > 10
                || choiceCount + blankCount < 2 || choiceCount + blankCount > 15) {
            throw new BusinessException("题量不合法：选择题 1-10、填空题 0-10，总数 2-15");
        }

        // 抽样出题素材（随机取至多 10 个片段）
        List<ChunkMeta> pool = chunkMetaMapper.selectList(
                new QueryWrapper<ChunkMeta>().eq("kb_id", kb.getId()));
        if (pool.size() < 2) {
            throw new BusinessException("该知识库解析内容不足，请先上传教材并等待解析完成");
        }
        Collections.shuffle(pool);
        List<ChunkMeta> material = pool.subList(0, Math.min(10, pool.size()));

        // 调大模型出题（失败自动重试一次）
        String json = callLlmForQuestions(material, choiceCount, blankCount);
        if (json == null) {
            log.warn("出题第一次失败，重试: kbId={}", kb.getId());
            json = callLlmForQuestions(material, choiceCount, blankCount);
        }
        if (json == null) {
            throw new BusinessException(500, "AI 出题失败，请稍后重试");
        }

        // 解析并入库
        List<JsonNode> items;
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode questions = root.get("questions");
            if (questions == null || !questions.isArray() || questions.isEmpty()) {
                throw new IllegalStateException("JSON 中无 questions 数组");
            }
            items = new ArrayList<>();
            questions.forEach(items::add);
        } catch (Exception e) {
            log.error("出题 JSON 解析失败: {}", json, e);
            throw new BusinessException(500, "AI 出题结果解析失败，请重试");
        }

        Exercise exercise = new Exercise();
        exercise.setUserId(user.userId());
        exercise.setKbId(kb.getId());
        exercise.setTitle("《" + kb.getName() + "》练习题");
        int realChoice = 0, realBlank = 0;
        for (JsonNode it : items) {
            if (it.path("type").asInt() == 1) realChoice++;
            else realBlank++;
        }
        exercise.setChoiceCount(realChoice);
        exercise.setBlankCount(realBlank);
        exercise.setTotalScore(items.size() * SCORE_PER_QUESTION);
        exercise.setStatus(0);
        exerciseMapper.insert(exercise);

        for (JsonNode it : items) {
            ExerciseQuestion q = new ExerciseQuestion();
            q.setExerciseId(exercise.getId());
            q.setType(it.path("type").asInt() == 2 ? 2 : 1);
            q.setQuestion(it.path("question").asText(""));
            if (q.getType() == 1 && it.has("options")) {
                q.setOptions(it.get("options").toString());
            }
            q.setAnswer(it.path("answer").asText(""));
            q.setExplanation(it.path("explanation").asText(""));
            q.setScore(SCORE_PER_QUESTION);
            long srcId = it.path("sourceChunkId").asLong(0);
            q.setSourceChunkId(srcId > 0 ? srcId : null);
            questionMapper.insert(q);
        }
        log.info("出题成功: exerciseId={}, 选择{}题 填空{}题", exercise.getId(), realChoice, realBlank);
        return detail(exercise.getId(), user);
    }

    private String callLlmForQuestions(List<ChunkMeta> material, int choiceCount, int blankCount) {
        try {
            String prompt = buildGeneratePrompt(material, choiceCount, blankCount);
            ChatResponse resp = chatModel.call(new Prompt(List.of(
                    new SystemMessage(prompt),
                    new UserMessage("请出题，直接输出 JSON，不要输出其他任何内容。"))));
            String text = resp.getResult().getOutput().getText();
            return extractJson(text);
        } catch (Exception e) {
            log.warn("出题调用大模型失败: {}", e.getMessage());
            return null;
        }
    }

    /** 出题提示词：严格 JSON 输出约束（设计文档 5.5） */
    private String buildGeneratePrompt(List<ChunkMeta> material, int choiceCount, int blankCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名严谨的大学课程出题老师。请仅根据下方教材片段出题。\n\n要求：\n");
        sb.append("1. 出 ").append(choiceCount).append(" 道单项选择题（type=1，四个选项 A/B/C/D，answer 为正确选项字母）；\n");
        if (blankCount > 0) {
            sb.append("2. 出 ").append(blankCount).append(" 道填空题（type=2，题干中用____表示空，answer 为正确答案，若有多个可接受答案用|分隔，如\"JVM|Java虚拟机\"）；\n");
        }
        sb.append("3. 每题附 explanation（一句话解析）和 sourceChunkId（出题依据的片段 id，必须从所给片段中选）；\n");
        sb.append("4. 题目必须能在教材片段中找到依据，禁止编造片段外的知识点；\n");
        sb.append("5. 只输出如下格式的 JSON，禁止输出 markdown 代码块标记或其他文字：\n");
        sb.append("{\"questions\":[{\"type\":1,\"question\":\"...\",\"options\":[{\"key\":\"A\",\"text\":\"...\"},{\"key\":\"B\",\"text\":\"...\"},{\"key\":\"C\",\"text\":\"...\"},{\"key\":\"D\",\"text\":\"...\"}],\"answer\":\"A\",\"explanation\":\"...\",\"sourceChunkId\":1}]}\n\n");
        sb.append("教材片段：\n");
        for (ChunkMeta c : material) {
            String content = c.getContent();
            if (content.length() > 600) {
                content = content.substring(0, 600) + "…";
            }
            sb.append("[片段 id=").append(c.getId()).append("]\n").append(content).append("\n\n");
        }
        return sb.toString();
    }

    /** 提取 JSON：剥离 markdown 代码围栏，截取首尾大括号 */
    private String extractJson(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.startsWith("```")) {
            t = t.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return t.substring(start, end + 1);
    }

    // ==================== 查询 ====================

    public List<Exercise> listMine(LoginUser user) {
        return exerciseMapper.selectList(new QueryWrapper<Exercise>()
                .eq("user_id", user.userId())
                .orderByDesc("id"));
    }

    /** 详情：待作答时隐藏 answer/explanation（防作弊） */
    public ExerciseDetail detail(Long id, LoginUser user) {
        Exercise ex = getForOwner(id, user);
        boolean answered = ex.getStatus() != null && ex.getStatus() == 1;
        List<ExerciseQuestion> qs = questionMapper.selectList(
                new QueryWrapper<ExerciseQuestion>().eq("exercise_id", id).orderByAsc("id"));
        List<ExerciseDetail.QuestionView> views = new ArrayList<>();
        for (ExerciseQuestion q : qs) {
            views.add(new ExerciseDetail.QuestionView(
                    q.getId(), q.getType(), q.getQuestion(),
                    parseOptions(q.getOptions()),
                    answered ? q.getAnswer() : null,
                    answered ? q.getExplanation() : null,
                    q.getScore(), q.getSourceChunkId(),
                    answered ? q.getUserAnswer() : null,
                    answered ? q.getIsCorrect() : null));
        }
        return new ExerciseDetail(ex, views);
    }

    private List<ExerciseDetail.Option> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(optionsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ExerciseDetail.Option.class));
        } catch (Exception e) {
            log.warn("选项 JSON 解析失败: {}", optionsJson);
            return List.of();
        }
    }

    public Exercise getForOwner(Long id, LoginUser user) {
        Exercise ex = exerciseMapper.selectById(id);
        if (ex == null) {
            throw new BusinessException(404, "练习不存在");
        }
        if (!ex.getUserId().equals(user.userId())) {
            throw new BusinessException(403, "无权访问该练习");
        }
        return ex;
    }

    // ==================== 判分 ====================

    /**
     * 提交作答并服务端判分（仅一次机会）
     */
    public ExerciseDetail submit(Long id, SubmitExerciseRequest req, LoginUser user) {
        Exercise ex = getForOwner(id, user);
        if (ex.getStatus() != null && ex.getStatus() == 1) {
            throw new BusinessException("该练习已作答，不能重复提交");
        }

        Map<Long, String> answerMap = new HashMap<>();
        for (SubmitExerciseRequest.AnswerItem item : req.getAnswers()) {
            if (item.questionId() != null) {
                answerMap.put(item.questionId(), item.answer());
            }
        }

        List<ExerciseQuestion> qs = questionMapper.selectList(
                new QueryWrapper<ExerciseQuestion>().eq("exercise_id", id).orderByAsc("id"));
        int total = 0;
        for (ExerciseQuestion q : qs) {
            String given = answerMap.get(q.getId());
            q.setUserAnswer(given);
            boolean correct = q.getType() == 2 ? gradeBlank(q.getAnswer(), given)
                                               : gradeChoice(q.getAnswer(), given);
            q.setIsCorrect(correct ? 1 : 0);
            if (correct) {
                total += q.getScore();
            }
            questionMapper.updateById(q);
        }
        ex.setScore(total);
        ex.setStatus(1);
        exerciseMapper.updateById(ex);
        log.info("练习判分完成: exerciseId={}, 得分 {}/{}", id, total, ex.getTotalScore());
        return detail(id, user);
    }

    /** 选择题：去空格忽略大小写精确匹配 */
    private boolean gradeChoice(String correct, String given) {
        return given != null && correct != null
                && correct.trim().equalsIgnoreCase(given.trim());
    }

    /** 填空题：多答案（| 分隔）任一匹配即对；归一化=全角转半角+去空格+小写 */
    private boolean gradeBlank(String correct, String given) {
        if (given == null || correct == null) {
            return false;
        }
        String g = normalize(given);
        if (g.isEmpty()) {
            return false;
        }
        for (String alt : correct.split("\\|")) {
            if (!normalize(alt).isEmpty() && normalize(alt).equals(g)) {
                return true;
            }
        }
        return false;
    }

    /** 全角→半角 + 去所有空白 + 小写（填空题宽容判分） */
    private String normalize(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.trim().toCharArray()) {
            if (c == '　') {
                sb.append(' ');
            } else if (c >= '！' && c <= '～') {
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase().replaceAll("\\s+", "");
    }
}
