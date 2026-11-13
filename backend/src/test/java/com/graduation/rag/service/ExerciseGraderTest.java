package com.graduation.rag.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务端判分纯函数测试：锁定防作弊判分契约（答案绝不下发前端，比对只在服务端）
 */
class ExerciseGraderTest {

    // ===== 选择题 =====

    @Test
    @DisplayName("选择题忽略大小写与首尾空格")
    void choiceIgnoresCaseAndSpace() {
        assertTrue(ExerciseGrader.gradeChoice("A", "a"));
        assertTrue(ExerciseGrader.gradeChoice("B", "  B  "));
        assertTrue(ExerciseGrader.gradeChoice("AB", "ab"));
    }

    @Test
    @DisplayName("选择题错误选项与空作答判错")
    void choiceWrongOrNull() {
        assertFalse(ExerciseGrader.gradeChoice("A", "B"));
        assertFalse(ExerciseGrader.gradeChoice("A", null));
        assertFalse(ExerciseGrader.gradeChoice(null, "A"));
        assertFalse(ExerciseGrader.gradeChoice("A", ""));
    }

    @Test
    @DisplayName("选择题不做归一化：全角 A 与半角 A 不相等")
    void choiceNoFullWidthNormalization() {
        assertFalse(ExerciseGrader.gradeChoice("A", "Ａ"));
    }

    // ===== 填空题 =====

    @Test
    @DisplayName("填空题多答案任一匹配即对")
    void blankMultipleAnswers() {
        assertTrue(ExerciseGrader.gradeBlank("JVM|Java虚拟机", "Java虚拟机"));
        assertTrue(ExerciseGrader.gradeBlank("JVM|Java虚拟机", "jvm"));
        assertFalse(ExerciseGrader.gradeBlank("JVM|Java虚拟机", "JRE"));
    }

    @Test
    @DisplayName("填空题全角半角与空白归一化")
    void blankNormalization() {
        assertTrue(ExerciseGrader.gradeBlank("HashMap", "ＨａｓｈＭａｐ"));
        assertTrue(ExerciseGrader.gradeBlank("HashMap", " hash map "));
        assertTrue(ExerciseGrader.gradeBlank("垃圾回收", "垃圾 回收"));
        assertTrue(ExerciseGrader.gradeBlank("垃圾回收", "　垃圾回收　"));
    }

    @Test
    @DisplayName("填空题空作答与 null 判错")
    void blankEmptyOrNull() {
        assertFalse(ExerciseGrader.gradeBlank("JVM", null));
        assertFalse(ExerciseGrader.gradeBlank("JVM", ""));
        assertFalse(ExerciseGrader.gradeBlank("JVM", "   "));
        assertFalse(ExerciseGrader.gradeBlank(null, "JVM"));
    }

    @Test
    @DisplayName("填空题大小写不敏感")
    void blankCaseInsensitive() {
        assertTrue(ExerciseGrader.gradeBlank("Spring Boot", "spring boot"));
    }

    // ===== 归一化 =====

    @Test
    @DisplayName("normalize：全角转半角、去空白、小写")
    void normalizeBehavior() {
        assertEquals("abc123", ExerciseGrader.normalize("ＡＢＣ１２３"));
        assertEquals("ab", ExerciseGrader.normalize(" a b "));
        assertEquals("a!~", ExerciseGrader.normalize("ａ！～"));
    }
}
