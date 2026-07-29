-- Replace external catalog images with project-owned local assets and refine
-- the development inventory without overwriting variants that already have
-- inventory movements.

UPDATE product_images pi
SET url = '/assets/images/products/' || p.slug || '.png',
    alt_text = p.name || ' em fundo claro',
    updated_at = CURRENT_TIMESTAMP
FROM products p
WHERE pi.product_id = p.id
  AND pi.main_image = true
  AND p.brand = 'FemStep';

WITH palette(slug, old_color, new_color, color_code) AS (
    VALUES
        ('scarpin-aurora', 'Preto', 'Vinho', 'VI'),
        ('scarpin-aurora', 'Caramelo', 'Preto', 'PR'),
        ('sandalia-serena', 'Preto', 'Nude', 'NU'),
        ('sandalia-serena', 'Caramelo', 'Caramelo', 'CA'),
        ('sapatilha-luna', 'Preto', 'Preto', 'PR'),
        ('sapatilha-luna', 'Caramelo', 'Nude', 'NU'),
        ('bota-iris', 'Preto', 'Caramelo', 'CA'),
        ('bota-iris', 'Caramelo', 'Preto', 'PR'),
        ('tenis-flora', 'Preto', 'Off-white', 'OW'),
        ('tenis-flora', 'Caramelo', 'Rosé', 'RO'),
        ('mocassim-alba', 'Preto', 'Preto', 'PR'),
        ('mocassim-alba', 'Caramelo', 'Caramelo', 'CA'),
        ('scarpin-celeste', 'Preto', 'Azul-celeste', 'AZ'),
        ('scarpin-celeste', 'Caramelo', 'Prata', 'PT'),
        ('sandalia-brisa', 'Preto', 'Caramelo', 'CA'),
        ('sandalia-brisa', 'Caramelo', 'Dourado', 'DO'),
        ('sapatilha-amelie', 'Preto', 'Rosé', 'RO'),
        ('sapatilha-amelie', 'Caramelo', 'Nude', 'NU'),
        ('bota-siena', 'Preto', 'Marrom-café', 'MC'),
        ('bota-siena', 'Caramelo', 'Preto', 'PR'),
        ('tenis-nuvem', 'Preto', 'Branco', 'BR'),
        ('tenis-nuvem', 'Caramelo', 'Areia', 'AR'),
        ('mocassim-olivia', 'Preto', 'Creme', 'CR'),
        ('mocassim-olivia', 'Caramelo', 'Preto', 'PR')
)
UPDATE product_variants pv
SET color = '__V3__' || palette.color_code,
    sku = 'FS-' || UPPER(REPLACE(p.slug, '-', '')) || '-' || pv.size || '-' || palette.color_code,
    updated_at = CURRENT_TIMESTAMP
FROM products p
JOIN palette ON palette.slug = p.slug
WHERE pv.product_id = p.id
  AND pv.color = palette.old_color
  AND p.brand = 'FemStep';

WITH palette(slug, new_color, color_code) AS (
    VALUES
        ('scarpin-aurora', 'Vinho', 'VI'),
        ('scarpin-aurora', 'Preto', 'PR'),
        ('sandalia-serena', 'Nude', 'NU'),
        ('sandalia-serena', 'Caramelo', 'CA'),
        ('sapatilha-luna', 'Preto', 'PR'),
        ('sapatilha-luna', 'Nude', 'NU'),
        ('bota-iris', 'Caramelo', 'CA'),
        ('bota-iris', 'Preto', 'PR'),
        ('tenis-flora', 'Off-white', 'OW'),
        ('tenis-flora', 'Rosé', 'RO'),
        ('mocassim-alba', 'Preto', 'PR'),
        ('mocassim-alba', 'Caramelo', 'CA'),
        ('scarpin-celeste', 'Azul-celeste', 'AZ'),
        ('scarpin-celeste', 'Prata', 'PT'),
        ('sandalia-brisa', 'Caramelo', 'CA'),
        ('sandalia-brisa', 'Dourado', 'DO'),
        ('sapatilha-amelie', 'Rosé', 'RO'),
        ('sapatilha-amelie', 'Nude', 'NU'),
        ('bota-siena', 'Marrom-café', 'MC'),
        ('bota-siena', 'Preto', 'PR'),
        ('tenis-nuvem', 'Branco', 'BR'),
        ('tenis-nuvem', 'Areia', 'AR'),
        ('mocassim-olivia', 'Creme', 'CR'),
        ('mocassim-olivia', 'Preto', 'PR')
)
UPDATE product_variants pv
SET color = palette.new_color,
    updated_at = CURRENT_TIMESTAMP
FROM products p
JOIN palette ON palette.slug = p.slug
WHERE pv.product_id = p.id
  AND pv.color = '__V3__' || palette.color_code
  AND p.brand = 'FemStep';

WITH inventory_target AS (
    SELECT
        pv.id,
        GREATEST(
            pv.reserved_quantity,
            CASE pv.size
                WHEN 33 THEN 5
                WHEN 34 THEN 8
                WHEN 35 THEN 13
                WHEN 36 THEN 18
                WHEN 37 THEN 16
                WHEN 38 THEN 12
                WHEN 39 THEN 8
                ELSE 5
            END
            + (p.id % 4)::INTEGER
            + CASE
                WHEN pv.color IN ('Preto', 'Nude', 'Branco', 'Caramelo') THEN 3
                ELSE 1
              END
        ) AS target_stock
    FROM product_variants pv
    JOIN products p ON p.id = pv.product_id
    WHERE p.brand = 'FemStep'
      AND NOT EXISTS (
          SELECT 1
          FROM inventory_movements im
          WHERE im.variant_id = pv.id
      )
)
UPDATE product_variants pv
SET stock_quantity = inventory_target.target_stock,
    updated_at = CURRENT_TIMESTAMP
FROM inventory_target
WHERE pv.id = inventory_target.id;

INSERT INTO inventory_movements (
    variant_id,
    order_id,
    movement_type,
    quantity,
    stock_before,
    stock_after,
    reason,
    created_by_user_id
)
SELECT
    pv.id,
    NULL,
    'INBOUND',
    pv.stock_quantity,
    0,
    pv.stock_quantity,
    'Estoque inicial do catálogo local com imagens próprias',
    NULL
FROM product_variants pv
JOIN products p ON p.id = pv.product_id
WHERE p.brand = 'FemStep'
  AND NOT EXISTS (
      SELECT 1
      FROM inventory_movements im
      WHERE im.variant_id = pv.id
  );
