package com.graduation.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graduation.rag.dto.ExerciseDetail;
import com.graduation.rag.dto.SubmitExerciseRequest;
import com.graduation.rag.entity.Exercise;
import com.graduation.rag.entity.ExerciseQuestion;
import com.graduation.rag.exception.BusinessException;
import com.graduation.rag.mapper.ChunkMetaMapper;
import com.graduation.rag.mapper.ExerciseMapper;
import com.graduation.rag.mapper.ExerciseQuestionMapper;
import com.graduation.rag.security.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提交流程测试：服务端判分累分、一次性提交、归属校验
 */
class ExerciseSubmitTest {

    private ExerciseMapper exerciseMapper;
    private ExerciseQuestionMapper questionMapper;
    private ExerciseService service;
    private final LoginUser owner = new LoginUser(1L, "alice", "student");

    private ExerciseQuestion q(long id, int type, String answer) {
        ExerciseQuestion q = new ExerciseQuestion();
        q.setId(id);
        q.setExerciseId(10L);
        q.setType(type);
        q.setAnswer(answer);
        q.setScore(5);
        return q;
    }

    @BeforeEach
    void setUp() {
        exerciseMapper = Mockito.mock(ExerciseMapper.class);
        questionMapper = Mockito.mock(ExerciseQuestionMapper.class);
        service = new ExerciseService(exerciseMapper, questionMapper,
                Mockito.mock(ChunkMetaMapper.class), Mockito.mock(KnowledgeBaseService.class),
                Mockito.mock(ChatModel.class), new ObjectMapper());

        Exercise ex = new Exercise();
        ex.setId(10L);
        ex.setUserId(1L);
        ex.setStatus(0);
        ex.setTotalScore(15);
        when(exerciseMapper.selectById(10L)).thenReturn(ex);

        List<ExerciseQuestion> qs = List.of(
                q(1L, 1, "B"),                 // 选择题
                q(2L, 2, "JVM|Java虚拟机"),    // 填空题多答案
                q(3L, 2, "HashMap"));          // 填空题单答案
        when(questionMapper.selectList(any())).thenReturn(new ArrayList<>(qs));
    }

    private SubmitExerciseRequest req(String a1, String a2, String a3) {
        SubmitExerciseRequest r = new SubmitExerciseRequest();
        r.setAnswers(List.of(
                new SubmitExerciseRequest.AnswerItem(1L, a1),
                new SubmitExerciseRequest.AnswerItem(2L, a2),
                new SubmitExerciseRequest.AnswerItem(3L, a3)));
        return r;
    }

    @Test
    @DisplayName("按对错累分：两对一错得 10 分并写判分标记")
    void scoresOnlyCorrectQuestions() {
        ExerciseDetail detail = service.submit(10L, req("b", "Java虚拟机", "TreeMap"), owner);

        assertEquals(10, detail.exercise().getScore());
        assertEquals(1, detail.exercise().getStatus());

        ArgumentCaptor<ExerciseQuestion> captor = ArgumentCaptor.forClass(ExerciseQuestion.class);
        verify(questionMapper, Mockito.times(3)).updateById(captor.capture());
        List<ExerciseQuestion> saved = captor.getAllValues();
        assertEquals(1, saved.get(0).getIsCorrect(), "小写 b 应判对（忽略大小写）");
        assertEquals(1, saved.get(1).getIsCorrect(), "多答案命中其一应判对");
        assertEquals(0, saved.get(2).getIsCorrect(), "错误答案应判错");
    }

    @Test
    @DisplayName("填空题全角作答也判对，错误作答不计分")
    void blankFullWidthAndWrong() {
        ExerciseDetail detail = service.submit(10L, req("A", "ＪＶＭ", "TreeMap"), owner);
        assertEquals(5, detail.exercise().getScore(), "仅全角 JVM 的填空题得 5 分");
    }

    @Test
    @DisplayName("已作答练习禁止重复提交")
    void submitTwiceRejected() {
        service.submit(10L, req("B", "JVM", "HashMap"), owner);
        assertThrows(BusinessException.class,
                () -> service.submit(10L, req("B", "JVM", "HashMap"), owner));
        verify(exerciseMapper, Mockito.times(1)).updateById(any(Exercise.class));
    }

    @Test
    @DisplayName("他人练习不可提交（归属校验）")
    void submitForeignExerciseRejected() {
        LoginUser stranger = new LoginUser(2L, "mallory", "student");
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.submit(10L, req("B", "JVM", "HashMap"), stranger));
        assertEquals(403, e.getCode());
        verify(questionMapper, never()).updateById(any(ExerciseQuestion.class));
    }

    @Test
    @DisplayName("详情接口待作答时不下发答案与解析（防作弊）")
    void detailHidesAnswerBeforeSubmit() {
        ExerciseDetail detail = service.detail(10L, owner);
        for (ExerciseDetail.QuestionView v : detail.questions()) {
            org.junit.jupiter.api.Assertions.assertNull(v.answer());
            org.junit.jupiter.api.Assertions.assertNull(v.explanation());
        }
    }

    @Test
    @DisplayName("提交后详情可见答案、解析与判分结果")
    void detailShowsAnswerAfterSubmit() {
        service.submit(10L, req("B", "JVM", "wrong"), owner);
        ExerciseDetail detail = service.detail(10L, owner);
        assertTrue(detail.questions().stream().allMatch(v -> v.answer() != null));
        assertEquals(1, detail.questions().get(0).isCorrect());
        assertEquals(0, detail.questions().get(2).isCorrect());
    }
}
