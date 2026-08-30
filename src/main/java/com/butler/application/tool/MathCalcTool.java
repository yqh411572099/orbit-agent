package com.butler.application.tool;

import com.butler.domain.agent.AgentTool;
import com.butler.domain.agent.ToolContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * 基础数学计算：对一组数字做加减乘除等确定性运算，避免模型心算出错。
 * 用 BigDecimal 逐步计算（不使用脚本引擎），结果按 scale 四舍五入。
 */
@Component
public class MathCalcTool implements AgentTool {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override public String name() { return "math_calc"; }

    @Override public String description() {
        return "基础数学计算：对数字做加/减/乘/除运算（支持一组数字按同一运算连算），"
                + "适用于求和、差值、平均值、单位换算、目标差额、周均量等加减乘除。";
    }

    @Override public String parametersSchema() {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode props = root.putObject("properties");
        ObjectNode op = props.putObject("operation");
        op.put("type", "string");
        op.put("description", "运算类型：add(加)、subtract(减)、multiply(乘)、divide(除)。对 numbers 数组按顺序连算。");
        op.putArray("enum").add("add").add("subtract").add("multiply").add("divide");
        com.fasterxml.jackson.databind.node.ObjectNode numbers = props.putObject("numbers");
        numbers.put("type", "array");
        numbers.put("description", "参与运算的数字，按运算顺序排列。如减法 [被减数, 减数]、除法 [被除数, 除数]；add 可放多个求和。");
        numbers.putObject("items").put("type", "number");
        props.putObject("scale").put("type", "integer")
                .put("description", "结果保留的小数位数，默认 2；不需要小数可填 0。");
        props.putObject("query").put("type", "string")
                .put("description", "可选，自然语言说明你在算什么（如“20斤目标按23周分摊”），便于结果可读。");
        root.putArray("required").add("operation").add("numbers");
        return root.toString();
    }

    @Override public String execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = mapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
            String op = args.path("operation").asText("").trim().toLowerCase();
            JsonNode nums = args.path("numbers");
            int scale = args.has("scale") && args.path("scale").isInt() ? args.path("scale").asInt() : 2;
            String note = args.path("query").asText("");
            if (!nums.isArray() || nums.size() < 2) {
                return "numbers 需要至少 2 个数字（减法 [被减数,减数]、除法 [被除数,除数]、加法可多个）。";
            }
            java.util.List<BigDecimal> values = new java.util.ArrayList<>();
            for (JsonNode n : nums) {
                if (!n.isNumber()) return "numbers 必须全部是数字，收到：" + n;
                values.add(n.decimalValue());
            }
            BigDecimal result = values.get(0);
            for (int i = 1; i < values.size(); i++) {
                BigDecimal v = values.get(i);
                result = switch (op) {
                    case "add" -> result.add(v);
                    case "subtract" -> result.subtract(v);
                    case "multiply" -> result.multiply(v);
                    case "divide" -> {
                        if (v.signum() == 0) yield null;
                        yield result.divide(v, Math.max(scale, 10), RoundingMode.HALF_UP);
                    }
                    default -> null;
                };
                if (result == null) break;
            }
            if (result == null) {
                return op.equals("divide") ? "除数不能为 0。" : "不支持的运算：" + op + "（可选 add/subtract/multiply/divide）。";
            }
            BigDecimal rounded = result.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros();
            StringBuilder sb = new StringBuilder();
            if (note != null && !note.isBlank()) sb.append("（").append(note).append("）\n");
            sb.append(opLabel(op)).append("结果：").append(rounded.toPlainString());
            return sb.toString();
        } catch (Exception e) {
            return "计算失败：" + e.getMessage();
        }
    }

    private String opLabel(String op) {
        return switch (op) {
            case "add" -> "求和";
            case "subtract" -> "相减";
            case "multiply" -> "相乘";
            case "divide" -> "相除";
            default -> "运算";
        };
    }
}
