CREATE TABLE IF NOT EXISTS menu_item_image (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id     BIGINT       NOT NULL COMMENT 'Menu item ID',
    image_path  VARCHAR(500) NOT NULL COMMENT 'Stored image path',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT 'Image order; the first image is the primary image',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_menu_item_image_item_sort (item_id, sort_order, id)
) COMMENT 'Menu item images';

INSERT INTO menu_item_image (item_id, image_path, sort_order, created_at)
SELECT item.id, item.image_path, 0, item.created_at
FROM menu_item item
WHERE item.image_path IS NOT NULL
  AND TRIM(item.image_path) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM menu_item_image image
      WHERE image.item_id = item.id
  );
