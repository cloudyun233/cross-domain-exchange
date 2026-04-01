#!/bin/bash
# ========================================
# 跨域数据交换系统 - 弱网模拟脚本
# 论文表4-4: tc弱网参数预设
# 使用: ./network-simulate.sh [场景编号]
# ========================================

INTERFACE=${INTERFACE:-eth0}

reset_network() {
    echo "[INFO] 重置网络到标准状态..."
    tc qdisc del dev $INTERFACE root 2>/dev/null
    echo "[OK] 网络已恢复正常"
}

scenario_standard() {
    echo "[场景1] 标准网络: 延迟10ms, 丢包0%"
    tc qdisc replace dev $INTERFACE root netem delay 10ms
}

scenario_gov_fluctuation() {
    echo "[场景2] 政务跨域波动: 延迟100ms±30ms, 丢包5%, 带宽10Mbps"
    tc qdisc replace dev $INTERFACE root netem \
        delay 100ms 30ms distribution normal \
        loss 5% \
        rate 10mbit
}

scenario_extreme() {
    echo "[场景3] 极端弱网: 延迟500ms±100ms, 丢包20%, 带宽1Mbps"
    tc qdisc replace dev $INTERFACE root netem \
        delay 500ms 100ms distribution normal \
        loss 20% \
        rate 1mbit
}

show_status() {
    echo "==== 当前网络模拟状态 ===="
    tc qdisc show dev $INTERFACE 2>/dev/null || echo "  (无模拟规则)"
    echo "========================="
}

case "${1:-menu}" in
    1|standard)   reset_network; scenario_standard ;;
    2|gov)        reset_network; scenario_gov_fluctuation ;;
    3|extreme)    reset_network; scenario_extreme ;;
    0|reset)      reset_network ;;
    status)       show_status ;;
    *)
        echo "========================================="
        echo " 跨域数据交换系统 - 弱网模拟"
        echo "========================================="
        echo " 0) 重置网络 (恢复正常)"
        echo " 1) 标准网络       (10ms, 0%丢包)"
        echo " 2) 政务跨域波动   (100ms, 5%丢包, 10Mbps)"
        echo " 3) 极端弱网       (500ms, 20%丢包, 1Mbps)"
        echo " status) 查看当前状态"
        echo "========================================="
        echo -n "请选择场景 [0-3]: "
        read choice
        exec "$0" "$choice"
        ;;
esac

show_status
