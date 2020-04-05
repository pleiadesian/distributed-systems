#include "qos.h"
#include "stdint.h"
#include "rte_common.h"
#include "rte_mbuf.h"
#include "rte_meter.h"
#include "rte_red.h"

// #define ANOTHER_WAY

#ifndef ANOTHER_WAY
struct rte_meter_srtcm_params app_srtcm_params[] = {
    {.cir = 1000000000 * 0.16, .cbs = 2000000, .ebs = 2000000},
    {.cir = 1000000000 * 0.08, .cbs = 760000, .ebs = 600000},
    {.cir = 1000000000 * 0.04, .cbs = 360000, .ebs = 300000},
    {.cir = 1000000000 * 0.02, .cbs = 160000, .ebs = 150000},
};

struct rte_red_params app_red_params[][e_RTE_METER_COLORS] = {
    {
        [e_RTE_METER_GREEN] = {.min_th = 1022, .max_th = 1023, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_YELLOW] = {.min_th = 0, .max_th = 1, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_RED] = {.min_th = 0, .max_th = 1, .maxp_inv = 10, .wq_log2 = 9},
    }
};
#else
struct rte_meter_srtcm_params app_srtcm_params[] = {
    {.cir = 1000000000 * 0.16, .cbs = 400000, .ebs = 400000},
};

struct rte_red_params app_red_params[][e_RTE_METER_COLORS] = {
    /* Traffic Class 0 Colors Green / Yellow / Red */
    {
        [e_RTE_METER_GREEN] = {.min_th = 1022, .max_th = 1023, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_YELLOW] = {.min_th = 1022, .max_th = 1023, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_RED] = {.min_th = 1022, .max_th = 1023, .maxp_inv = 10, .wq_log2 = 9},
    },
    {
        [e_RTE_METER_GREEN] = {.min_th = 1022, .max_th = 1023, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_YELLOW] = {.min_th = 1022, .max_th = 1023, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_RED] = {.min_th = 0, .max_th = 1, .maxp_inv = 10, .wq_log2 = 9},
    },
    {
        [e_RTE_METER_GREEN] = {.min_th = 1022, .max_th = 1023, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_YELLOW] = {.min_th = 0, .max_th = 1, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_RED] = {.min_th = 0, .max_th = 1, .maxp_inv = 10, .wq_log2 = 9},
    },
    {
        [e_RTE_METER_GREEN] = {.min_th = 0, .max_th = 45, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_YELLOW] = {.min_th = 0, .max_th = 1, .maxp_inv = 10, .wq_log2 = 9},
        [e_RTE_METER_RED] = {.min_th = 0, .max_th = 1, .maxp_inv = 10, .wq_log2 = 9},
    },
};
#endif

struct rte_red_config app_red_cfg[APP_FLOWS_MAX][e_RTE_METER_COLORS];

struct rte_meter_srtcm app_flows[APP_FLOWS_MAX];

struct red_queue {
    uint32_t size;
    struct rte_red red;
} app_queue[APP_FLOWS_MAX][e_RTE_METER_COLORS];

uint64_t tsc_hz;
uint64_t base_cycles[APP_FLOWS_MAX];
uint64_t last_time;

/**
 * This function will be called only once at the beginning of the test. 
 * You can initialize your meter here.
 * 
 * int rte_meter_srtcm_config(struct rte_meter_srtcm *m, struct rte_meter_srtcm_params *params);
 * @return: 0 upon success, error code otherwise
 * 
 * void rte_exit(int exit_code, const char *format, ...)
 * #define rte_panic(...) rte_panic_(__func__, __VA_ARGS__, "dummy")
 * 
 * uint64_t rte_get_tsc_hz(void)
 * @return: The frequency of the RDTSC timer resolution
 * 
 * static inline uint64_t rte_get_tsc_cycles(void)
 * @return: The time base for this lcore.
 */
int
qos_meter_init(void)
{
    uint32_t i, j;
    int ret;

    tsc_hz = rte_get_tsc_hz();

    for (i = 0, j = 0; i < APP_FLOWS_MAX; i++, j = (j + 1) % RTE_DIM(app_srtcm_params)) {
        ret = rte_meter_srtcm_config(&app_flows[i], &app_srtcm_params[j]);
        if (ret)
            rte_exit(EXIT_FAILURE, "rte_meter_srtcm_config failed\n");
        base_cycles[i] = rte_get_tsc_cycles();
    }

    return 0;
}

/**
 * This function will be called for every packet in the test, 
 * after which the packet is marked by returning the corresponding color.
 * 
 * A packet is marked green if it doesn't exceed the CBS, 
 * yellow if it does exceed the CBS, but not the EBS, and red otherwise
 * 
 * The pkt_len is in bytes, the time is in nanoseconds.
 * 
 * Point: We need to convert ns to cpu circles
 * Point: Time is not counted from 0
 * 
 * static inline enum rte_meter_color rte_meter_srtcm_color_blind_check(struct rte_meter_srtcm *m,
	uint64_t time, uint32_t pkt_len)
 * 
 * enum qos_color { GREEN = 0, YELLOW, RED };
 * enum rte_meter_color { e_RTE_METER_GREEN = 0, e_RTE_METER_YELLOW,  
	e_RTE_METER_RED, e_RTE_METER_COLORS };
 */ 
enum qos_color
qos_meter_run(uint32_t flow_id, uint32_t pkt_len, uint64_t time)
{
    uint64_t cpu_cycles = base_cycles[flow_id] + (uint64_t)(time / 1000000000 * tsc_hz);
    return rte_meter_srtcm_color_blind_check(&app_flows[flow_id], cpu_cycles, pkt_len);
}


/**
 * This function will be called only once at the beginning of the test. 
 * You can initialize you dropper here
 * 
 * int rte_red_rt_data_init(struct rte_red *red);
 * @return Operation status, 0 success
 * 
 * int rte_red_config_init(struct rte_red_config *red_cfg, const uint16_t wq_log2, 
   const uint16_t min_th, const uint16_t max_th, const uint16_t maxp_inv);
 * @return Operation status, 0 success 
 */
int
qos_dropper_init(void)
{
    uint32_t i, j;
    int ret;

    last_time = 0;
    for (i = 0; i < APP_FLOWS_MAX; i++) {
        for (j = 0; j < e_RTE_METER_COLORS; j++) {
            app_queue[i][j].size = 0;
            ret = rte_red_rt_data_init(&app_queue[i][j].red);
            if (ret) 
                rte_exit(EXIT_FAILURE, "rte_red_rt_data_init failed\n");
            ret = rte_red_config_init(
                &app_red_cfg[i][j],
                app_red_params[i % RTE_DIM(app_red_params)][j].wq_log2,
                app_red_params[i % RTE_DIM(app_red_params)][j].min_th,
                app_red_params[i % RTE_DIM(app_red_params)][j].max_th,
                app_red_params[i % RTE_DIM(app_red_params)][j].maxp_inv);
            if (ret)
                rte_exit(EXIT_FAILURE, "rte_red_config_init failed\n");
        }
    }
     
    return 0;
}

/**
 * This function will be called for every tested packet after being marked by the meter, 
 * and will make the decision whether to drop the packet by returning the decision (0 pass, 1 drop)
 * 
 * The probability of drop increases as the estimated average queue size grows
 * 
 * static inline void rte_red_mark_queue_empty(struct rte_red *red, const uint64_t time)
 * @brief Callback to records time that queue became empty
 * @param q_time : Start of the queue idle time (q_time) 
 * 
 * static inline int rte_red_enqueue(const struct rte_red_config *red_cfg,
	struct rte_red *red, const unsigned q, const uint64_t time)
 * @param q [in] updated queue size in packets   
 * @return Operation status
 * @retval 0 enqueue the packet
 * @retval 1 drop the packet based on max threshold criteria
 * @retval 2 drop the packet based on mark probability criteria
 */
int
qos_dropper_run(uint32_t flow_id, enum qos_color color, uint64_t time)
{
    uint32_t i, j;
    struct red_queue *q = &app_queue[flow_id][color];

    if (time != last_time) {
        for (i = 0; i < APP_FLOWS_MAX; i++) {
            for (j = 0; j < e_RTE_METER_COLORS; j++) {
                app_queue[i][j].size = 0;
                rte_red_mark_queue_empty(&app_queue[i][j].red, time);
            }
        }
        last_time = time;
    }

    if (rte_red_enqueue(&app_red_cfg[flow_id][color], &q->red, q->size, time) == 0) {
        q->size++;
        return 0;
    } else 
        return 1;
}
