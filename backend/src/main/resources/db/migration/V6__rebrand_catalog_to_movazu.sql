ALTER TABLE products
    ALTER COLUMN brand SET DEFAULT 'MOVAZU';

UPDATE products
SET brand = 'MOVAZU',
    seo_title = CASE
        WHEN seo_title IS NULL THEN NULL
        ELSE REPLACE(seo_title, 'FemStep', 'MOVAZU')
    END,
    seo_description = CASE
        WHEN seo_description IS NULL THEN NULL
        ELSE REPLACE(seo_description, 'FemStep', 'MOVAZU')
    END
WHERE brand = 'FemStep'
   OR seo_title LIKE '%FemStep%'
   OR seo_description LIKE '%FemStep%';
