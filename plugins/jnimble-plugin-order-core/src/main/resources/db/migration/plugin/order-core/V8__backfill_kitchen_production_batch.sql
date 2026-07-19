UPDATE ord_kitchen_queue_item
SET production_batch_no = CONCAT('LEGACY-', id)
WHERE status = 'COOKING'
  AND dispatch_mode = 'PAPERLESS'
  AND (production_batch_no IS NULL OR production_batch_no = '');
