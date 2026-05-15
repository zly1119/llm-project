package com.edu.llm.kg.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 图谱未完整命中章节时的关键词推断。
 */
public final class ChapterKeywordInference {

    private static final List<String> CALCULUS_CHAPTERS =
            List.of(
                    "第一章 函数、极限与连续",
                    "第二章 一元函数微分学：导数与微分",
                    "第三章 导数的应用与中值定理",
                    "第四章 一元函数积分学：不定积分",
                    "第五章 一元函数积分学：定积分",
                    "第六章 定积分的应用",
                    "第七章 常微分方程",
                    "第九章 多元函数微分学",
                    "第十章 多元函数积分学：二重积分与三重积分",
                    "第十一章 曲线积分与曲面积分",
                    "第十二章 无穷级数");

    private static final List<String> HIGHER_MATH_CHAPTERS =
            List.of(
                    "第一章 函数、极限与连续",
                    "第二章 一元函数微分学：导数与微分",
                    "第三章 导数的应用与中值定理",
                    "第四章 一元函数积分学：不定积分",
                    "第五章 一元函数积分学：定积分",
                    "第六章 定积分的应用",
                    "第七章 常微分方程",
                    "第八章 向量代数与空间解析几何",
                    "第九章 多元函数微分学",
                    "第十章 多元函数积分学：二重积分与三重积分",
                    "第十一章 曲线积分与曲面积分",
                    "第十二章 无穷级数");

    private ChapterKeywordInference() {}

    public static LinkedHashSet<String> inferChaptersFromQuestion(String question) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String q = normalize(question);

        result.addAll(expandBroadTopic(q));

        Map<String, String> keywordToChapter = new LinkedHashMap<>();
        keywordToChapter.put("函数|极限|连续|无穷小|无穷大", "第一章 函数、极限与连续");
        keywordToChapter.put("导数|微分|求导|切线|斜率|高阶导", "第二章 一元函数微分学：导数与微分");
        keywordToChapter.put("中值定理|洛必达|单调|极值|凹凸|曲率|近似", "第三章 导数的应用与中值定理");
        keywordToChapter.put("不定积分|原函数|换元积分|分部积分|有理函数积分", "第四章 一元函数积分学：不定积分");
        keywordToChapter.put("定积分|牛顿莱布尼茨|反常积分|积分上限函数", "第五章 一元函数积分学：定积分");
        keywordToChapter.put("面积|体积|弧长|旋转体|物理应用", "第六章 定积分的应用");
        keywordToChapter.put("微分方程|可分离变量|齐次|一阶线性|高阶线性", "第七章 常微分方程");
        keywordToChapter.put("向量|空间解析几何|数量积|向量积|平面|直线|曲面|空间曲线", "第八章 向量代数与空间解析几何");
        keywordToChapter.put("多元函数|偏导数|全微分|梯度|方向导数|多元极值|拉格朗日", "第九章 多元函数微分学");
        keywordToChapter.put("二重积分|三重积分|重积分|含参积分", "第十章 多元函数积分学：二重积分与三重积分");
        keywordToChapter.put("曲线积分|曲面积分|格林公式|高斯公式|斯托克斯公式|通量|旋度", "第十一章 曲线积分与曲面积分");
        keywordToChapter.put("级数|常数项级数|幂级数|傅里叶级数|收敛|展开", "第十二章 无穷级数");

        for (Map.Entry<String, String> entry : keywordToChapter.entrySet()) {
            if (q.matches(".*(" + entry.getKey() + ").*")) {
                result.add(entry.getValue());
            }
        }

        if (result.isEmpty()) {
            if (containsAny(q, "向量", "空间", "几何", "平面", "直线")) {
                result.add("第八章 向量代数与空间解析几何");
            } else if (containsAny(q, "微分", "导数", "求导")) {
                result.add("第二章 一元函数微分学：导数与微分");
            } else if (q.contains("积分") && !q.contains("不定")) {
                result.add("第五章 一元函数积分学：定积分");
            } else if (q.contains("极限")) {
                result.add("第一章 函数、极限与连续");
            } else {
                result.add("第一章 函数、极限与连续");
                result.add("第二章 一元函数微分学：导数与微分");
            }
        }
        return result;
    }

    public static LinkedHashSet<String> expandBroadTopic(String question) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String q = normalize(question);

        if (containsAny(q, "高等数学", "高数")) {
            result.addAll(HIGHER_MATH_CHAPTERS);
        }
        if (containsAny(q, "微积分", "calculus")) {
            result.addAll(CALCULUS_CHAPTERS);
        }
        if (containsAny(q, "多元微积分")) {
            result.add("第九章 多元函数微分学");
            result.add("第十章 多元函数积分学：二重积分与三重积分");
            result.add("第十一章 曲线积分与曲面积分");
        }
        if (containsAny(q, "积分学")) {
            result.add("第四章 一元函数积分学：不定积分");
            result.add("第五章 一元函数积分学：定积分");
            result.add("第六章 定积分的应用");
            result.add("第十章 多元函数积分学：二重积分与三重积分");
            result.add("第十一章 曲线积分与曲面积分");
        }
        return result;
    }

    private static String normalize(String question) {
        return question == null ? "" : question.trim().toLowerCase();
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
