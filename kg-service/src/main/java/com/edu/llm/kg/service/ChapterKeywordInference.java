package com.edu.llm.kg.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * 图谱未命中章节时的关键词推断（与单体 Main 中逻辑一致）。
 */
public final class ChapterKeywordInference {

    private ChapterKeywordInference() {}

    public static LinkedHashSet<String> inferChaptersFromQuestion(String question) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String q = question == null ? "" : question.toLowerCase();

        Map<String, String> keywordToChapter = new LinkedHashMap<>();
        keywordToChapter.put("函数|极限|连续|无穷小|无穷大", "第一章 函数与极限");
        keywordToChapter.put("导数|微分|求导|切线|斜率|高阶导", "第二章 导数与微分");
        keywordToChapter.put("中值定理|洛必达|泰勒|单调|极值|凹凸|曲率|方程近似解", "第三章 微分中值定理与导数的应用");
        keywordToChapter.put("不定积分|原函数|换元积分|分部积分|有理函数积分", "第四章 不定积分");
        keywordToChapter.put("定积分|牛顿|莱布尼茨|反常积分|积分上限函数", "第五章 定积分");
        keywordToChapter.put("定积分应用|面积|体积|弧长|元素法|物理应用", "第六章 定积分的应用");
        keywordToChapter.put("微分方程|可分离|齐次|一阶线性|高阶线性|常系数", "第七章 微分方程");
        keywordToChapter.put("向量|空间向量|数量积|向量积|混合积|平面|直线|曲面|空间曲线", "第八章 向量代数与空间解析几何");
        keywordToChapter.put("多元函数|偏导数|全微分|梯度|方向导数|多元极值|拉格朗日乘数法", "第九章 多元函数微分法及其应用");
        keywordToChapter.put("二重积分|三重积分|重积分应用|含参积分", "第十章 重积分");
        keywordToChapter.put("曲线积分|曲面积分|格林公式|高斯公式|斯托克斯公式|通量|旋度", "第十一章 曲线积分与曲面积分");
        keywordToChapter.put("级数|常数项级数|幂级数|傅里叶级数|收敛|展开", "第十二章 无穷级数");

        for (Map.Entry<String, String> entry : keywordToChapter.entrySet()) {
            String regex = entry.getKey();
            String chapter = entry.getValue();
            if (q.matches(".*(" + regex + ").*")) {
                result.add(chapter);
            }
        }

        if (result.isEmpty()) {
            if (q.contains("向量") || q.contains("空间") || q.contains("几何") || q.contains("平面") || q.contains("直线")) {
                result.add("第八章 向量代数与空间解析几何");
            } else if (q.contains("微分") || q.contains("导数") || q.contains("求导")) {
                result.add("第二章 导数与微分");
            } else if (q.contains("积分") && !q.contains("不定") && !q.contains("定")) {
                result.add("第五章 定积分");
            } else if (q.contains("极限")) {
                result.add("第一章 函数与极限");
            } else {
                result.add("第一章 函数与极限");
                result.add("第二章 导数与微分");
            }
        }
        return result;
    }
}
