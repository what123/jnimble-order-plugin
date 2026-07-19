package com.jnimble.plugin.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnimble.plugin.order.model.entity.KitchenQueueItemEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KitchenQueueItemMapper extends BaseMapper<KitchenQueueItemEntity> {

    @Update("UPDATE ord_kitchen_queue_item SET status = 'COOKING', production_batch_no = #{batchNo}, "
            + "started_at = #{at}, updated_at = #{at} "
            + "WHERE id = #{id} AND status = 'WAITING' AND dispatch_mode = 'PAPERLESS'")
    int start(@Param("id") Long id, @Param("batchNo") String batchNo, @Param("at") LocalDateTime at);

    @Update("UPDATE ord_kitchen_queue_item SET status = 'COMPLETED', completed_at = #{at}, updated_at = #{at} "
            + "WHERE id = #{id} AND status = 'COOKING' AND dispatch_mode = 'PAPERLESS'")
    int complete(@Param("id") Long id, @Param("at") LocalDateTime at);

    @Update("UPDATE ord_kitchen_queue_item SET status = 'COMPLETED', completed_at = #{at}, updated_at = #{at} "
            + "WHERE production_batch_no = #{batchNo} AND status = 'COOKING' AND dispatch_mode = 'PAPERLESS'")
    int completeProductionBatch(@Param("batchNo") String batchNo, @Param("at") LocalDateTime at);

    @Update("UPDATE ord_kitchen_queue_item SET status = 'PRINTED', print_job_id = #{jobId}, "
            + "printed_at = #{at}, updated_at = #{at} "
            + "WHERE id = #{id} AND status = 'WAITING' AND dispatch_mode = 'PRINT'")
    int markPrinted(@Param("id") Long id, @Param("jobId") String jobId, @Param("at") LocalDateTime at);
}
