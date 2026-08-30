package com.butler.application.tool;

import com.butler.domain.agent.ToolCategory;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 计算器工具大类：纯确定性计算（数学运算、日历/日期推算）。
 * 把“算”这件事从模型心算里剥离，结果可复现、可单测；与外部信息类工具（联网/地图/知识库）平级，模型按需自选。
 */
@Component
public class CalculatorCategory extends ToolCategory {
    public CalculatorCategory(MathCalcTool mathCalcTool, CalendarCalcTool calendarCalcTool) {
        super("Calculator",
                "确定性计算工具，包含 math_calc（数学运算）和 calendar_calc（日期运算）两个子工具。"
                        + "总规则：只要最终结论里要用到本子工具能算出的数字结果，就必须调用对应子工具取得，"
                        + "不允许在回答正文里直接列算式或直接给运算结论；工具不支持的更复杂领域计算可自行处理。"
                        + "子工具见下，按要算的类型选择：",
                List.of(mathCalcTool, calendarCalcTool));
    }
}
