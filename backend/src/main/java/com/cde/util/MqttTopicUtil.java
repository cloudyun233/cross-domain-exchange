package com.cde.util;

/**
 * MQTT主题匹配工具类，实现MQTT 5.0通配符语义。
 * <p>
 * 通配符说明：
 * <ul>
 *   <li>{@code +} — 单层通配符，匹配恰好一个主题层级</li>
 *   <li>{@code #} — 多层通配符，匹配当前层级及所有子层级（必须位于过滤器末尾）</li>
 * </ul>
 */
public class MqttTopicUtil {

    private MqttTopicUtil() {
    }

    /**
     * 判断目标主题是否匹配订阅过滤器。
     * <p>
     * 匹配算法步骤：
     * <ol>
     *   <li>完全相等或过滤器为"#"时直接匹配</li>
     *   <li>按"/"分割过滤器和主题为层级数组</li>
     *   <li>逐层比较：遇到"#"则匹配剩余所有层级，遇到"+"则跳过当前层级，否则要求精确相等</li>
     *   <li>所有层级比较完毕后，要求两者层级数相同才算匹配</li>
     * </ol>
     *
     * @param filter 订阅过滤器，可包含+和#通配符
     * @param topic  目标主题，不含通配符
     * @return 是否匹配
     */
    public static boolean matchesTopic(String filter, String topic) {
        // 完全相等或全匹配通配符
        if (filter.equals(topic) || "#".equals(filter)) {
            return true;
        }

        String[] filterParts = filter.split("/");
        String[] topicParts = topic.split("/");
        for (int i = 0; i < filterParts.length; i++) {
            // 遇到#通配符，匹配剩余所有层级
            if ("#".equals(filterParts[i])) {
                return true;
            }
            // 过滤器层级多于主题层级，无法匹配
            if (i >= topicParts.length) {
                return false;
            }
            // 当前层级非+通配符且不相等，匹配失败
            if (!"+".equals(filterParts[i]) && !filterParts[i].equals(topicParts[i])) {
                return false;
            }
        }
        // 所有层级匹配后，要求层级数相同
        return filterParts.length == topicParts.length;
    }
}
