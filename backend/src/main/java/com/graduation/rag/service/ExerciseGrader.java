package com.graduation.rag.service;

/**
 * 练习判分纯函数（设计文档 5.5 服务端判分）
 *
 * 从 ExerciseService 抽出为静态工具类，便于单元测试直接覆盖判分契约：
 * - 选择题：去首尾空格 + 忽略大小写的精确匹配
 * - 填空题：多答案（| 分隔）任一匹配即对；归一化 = 全角转半角 + 去所有空白 + 小写
 *
 * 抽出为独立类的原因：判分是防作弊的核心逻辑（答案绝不下发前端、比对只在服务端），
 * 必须有无 Spring 容器、无数据库依赖的测试用例锁定其行为。
 */
public final class ExerciseGrader {

    private ExerciseGrader() {
    }

    /** 选择题：去首尾空格、忽略大小写的精确匹配；任一侧为 null 判错 */
    public static boolean gradeChoice(String correct, String given) {
        return given != null && correct != null
                && correct.trim().equalsIgnoreCase(given.trim());
    }

    /** 填空题：多答案（| 分隔）任一匹配即对；作答为空判错 */
    public static boolean gradeBlank(String correct, String given) {
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
    public static String normalize(String s) {
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
