-- Complete the first production-oriented data model and provide a usable
-- development catalog. All monetary values use NUMERIC and all external
-- operations have idempotency/audit support.

INSERT INTO roles (name, description)
VALUES
    ('ROLE_CUSTOMER', 'Customer'),
    ('ROLE_ADMIN', 'Administrator')
ON CONFLICT (name) DO NOTHING;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS short_description VARCHAR(500),
    ADD COLUMN IF NOT EXISTS compare_at_price NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS material VARCHAR(120),
    ADD COLUMN IF NOT EXISTS brand VARCHAR(120) NOT NULL DEFAULT 'FemStep',
    ADD COLUMN IF NOT EXISTS weight_kg NUMERIC(8,3) NOT NULL DEFAULT 0.700,
    ADD COLUMN IF NOT EXISTS height_cm NUMERIC(8,2) NOT NULL DEFAULT 12,
    ADD COLUMN IF NOT EXISTS width_cm NUMERIC(8,2) NOT NULL DEFAULT 18,
    ADD COLUMN IF NOT EXISTS length_cm NUMERIC(8,2) NOT NULL DEFAULT 30,
    ADD COLUMN IF NOT EXISTS seo_title VARCHAR(255),
    ADD COLUMN IF NOT EXISTS seo_description VARCHAR(500),
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;

ALTER TABLE products
    ADD CONSTRAINT chk_products_base_price_positive CHECK (base_price >= 0),
    ADD CONSTRAINT chk_products_compare_price CHECK (compare_at_price IS NULL OR compare_at_price >= base_price),
    ADD CONSTRAINT chk_products_dimensions CHECK (
        weight_kg > 0 AND height_cm > 0 AND width_cm > 0 AND length_cm > 0
    );

ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS reserved_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE product_variants
    ADD CONSTRAINT chk_variant_stock_non_negative CHECK (stock_quantity >= 0),
    ADD CONSTRAINT chk_variant_reserved_non_negative CHECK (reserved_quantity >= 0),
    ADD CONSTRAINT chk_variant_reserved_within_stock CHECK (reserved_quantity <= stock_quantity),
    ADD CONSTRAINT chk_variant_size CHECK (size BETWEEN 25 AND 50),
    ADD CONSTRAINT chk_variant_price_override CHECK (price_override IS NULL OR price_override >= 0);

CREATE INDEX IF NOT EXISTS idx_variants_catalog
    ON product_variants (active, size, color, stock_quantity);

CREATE TABLE addresses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label VARCHAR(80) NOT NULL DEFAULT 'Principal',
    recipient_name VARCHAR(255) NOT NULL,
    postal_code VARCHAR(8) NOT NULL,
    street VARCHAR(255) NOT NULL,
    number VARCHAR(40) NOT NULL,
    complement VARCHAR(255),
    neighborhood VARCHAR(160) NOT NULL,
    city VARCHAR(160) NOT NULL,
    state CHAR(2) NOT NULL,
    phone VARCHAR(20),
    default_address BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_addresses_postal_code CHECK (postal_code ~ '^[0-9]{8}$'),
    CONSTRAINT chk_addresses_state CHECK (state ~ '^[A-Z]{2}$')
);

CREATE UNIQUE INDEX uq_addresses_default_per_user
    ON addresses(user_id) WHERE default_address = true;
CREATE INDEX idx_addresses_user ON addresses(user_id);

CREATE TABLE auth_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    user_agent VARCHAR(500),
    ip_address VARCHAR(64),
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_auth_sessions_user ON auth_sessions(user_id);
CREATE INDEX idx_auth_sessions_expiry ON auth_sessions(expires_at);

CREATE TABLE email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE carts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_cart_status CHECK (status IN ('ACTIVE', 'CONVERTED', 'ABANDONED'))
);
CREATE UNIQUE INDEX uq_active_cart_per_user
    ON carts(user_id) WHERE user_id IS NOT NULL AND status = 'ACTIVE';

CREATE TABLE cart_items (
    id BIGSERIAL PRIMARY KEY,
    cart_id BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    variant_id BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cart_variant UNIQUE (cart_id, variant_id),
    CONSTRAINT chk_cart_item_quantity CHECK (quantity > 0)
);
CREATE INDEX idx_cart_items_cart ON cart_items(cart_id);

CREATE TABLE coupons (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(60) NOT NULL UNIQUE,
    description VARCHAR(255),
    discount_type VARCHAR(20) NOT NULL,
    discount_value NUMERIC(10,2) NOT NULL,
    minimum_order_value NUMERIC(10,2) NOT NULL DEFAULT 0,
    maximum_discount_value NUMERIC(10,2),
    usage_limit INTEGER,
    usage_limit_per_user INTEGER NOT NULL DEFAULT 1,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_coupon_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED')),
    CONSTRAINT chk_coupon_value CHECK (discount_value > 0),
    CONSTRAINT chk_coupon_period CHECK (ends_at > starts_at)
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    public_number VARCHAR(32) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(40) NOT NULL,
    payment_status VARCHAR(40) NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    customer_document VARCHAR(20),
    recipient_name VARCHAR(255) NOT NULL,
    shipping_postal_code VARCHAR(8) NOT NULL,
    shipping_street VARCHAR(255) NOT NULL,
    shipping_number VARCHAR(40) NOT NULL,
    shipping_complement VARCHAR(255),
    shipping_neighborhood VARCHAR(160) NOT NULL,
    shipping_city VARCHAR(160) NOT NULL,
    shipping_state CHAR(2) NOT NULL,
    shipping_service VARCHAR(120) NOT NULL,
    shipping_estimated_days INTEGER,
    subtotal NUMERIC(12,2) NOT NULL,
    discount_total NUMERIC(12,2) NOT NULL DEFAULT 0,
    shipping_total NUMERIC(12,2) NOT NULL DEFAULT 0,
    grand_total NUMERIC(12,2) NOT NULL,
    coupon_id BIGINT REFERENCES coupons(id) ON DELETE SET NULL,
    idempotency_key VARCHAR(80) NOT NULL UNIQUE,
    notes VARCHAR(1000),
    paid_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_order_status CHECK (status IN (
        'PENDING_PAYMENT', 'PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED',
        'CANCELLED', 'PAYMENT_FAILED', 'REFUNDED', 'PARTIALLY_REFUNDED'
    )),
    CONSTRAINT chk_order_payment_status CHECK (payment_status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED',
        'REFUNDED', 'CHARGEBACK'
    )),
    CONSTRAINT chk_order_totals CHECK (
        subtotal >= 0 AND discount_total >= 0 AND shipping_total >= 0 AND grand_total >= 0
    )
);
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);
CREATE INDEX idx_orders_status ON orders(status, created_at DESC);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    variant_id BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    sku VARCHAR(255) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    product_slug VARCHAR(255) NOT NULL,
    image_url VARCHAR(500),
    size INTEGER NOT NULL,
    color VARCHAR(255) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    quantity INTEGER NOT NULL,
    line_total NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_item_amounts CHECK (unit_price >= 0 AND line_total >= 0)
);
CREATE INDEX idx_order_items_order ON order_items(order_id);

CREATE TABLE order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    previous_status VARCHAR(40),
    new_status VARCHAR(40) NOT NULL,
    reason VARCHAR(500),
    changed_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_order_history_order ON order_status_history(order_id, created_at);

CREATE TABLE stock_reservations (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    variant_id BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP NOT NULL,
    released_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_order_variant_reservation UNIQUE (order_id, variant_id),
    CONSTRAINT chk_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT chk_reservation_status CHECK (status IN ('ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED'))
);
CREATE INDEX idx_stock_reservations_expiry ON stock_reservations(status, expires_at);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    provider VARCHAR(40) NOT NULL,
    provider_order_id VARCHAR(160),
    provider_payment_id VARCHAR(160),
    method VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    installments INTEGER,
    idempotency_key VARCHAR(80) NOT NULL UNIQUE,
    pix_qr_code TEXT,
    pix_ticket_url VARCHAR(1000),
    expires_at TIMESTAMP,
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_payment_amount CHECK (amount >= 0)
);
CREATE INDEX idx_payments_order ON payments(order_id);
CREATE INDEX idx_payments_provider_id ON payments(provider, provider_payment_id);

CREATE TABLE payment_events (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT REFERENCES payments(id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    provider_event_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_provider_event UNIQUE (provider, provider_event_id)
);

CREATE TABLE shipments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    provider VARCHAR(60) NOT NULL,
    provider_shipment_id VARCHAR(160),
    service_name VARCHAR(120) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    tracking_code VARCHAR(160),
    label_url VARCHAR(1000),
    estimated_days INTEGER,
    shipped_at TIMESTAMP,
    delivered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE inventory_movements (
    id BIGSERIAL PRIMARY KEY,
    variant_id BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    order_id BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    movement_type VARCHAR(30) NOT NULL,
    quantity INTEGER NOT NULL,
    stock_before INTEGER NOT NULL,
    stock_after INTEGER NOT NULL,
    reason VARCHAR(500),
    created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_inventory_movement_type CHECK (movement_type IN (
        'INBOUND', 'SALE', 'CANCELLATION', 'RETURN', 'ADJUSTMENT', 'RESERVATION', 'RELEASE'
    )),
    CONSTRAINT chk_inventory_after_non_negative CHECK (stock_after >= 0)
);
CREATE INDEX idx_inventory_variant_created ON inventory_movements(variant_id, created_at DESC);

CREATE TABLE coupon_redemptions (
    id BIGSERIAL PRIMARY KEY,
    coupon_id BIGINT NOT NULL REFERENCES coupons(id) ON DELETE RESTRICT,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id) ON DELETE RESTRICT,
    discount_amount NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_coupon_redemptions_user ON coupon_redemptions(coupon_id, user_id);

CREATE TABLE favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_favorite_user_product UNIQUE (user_id, product_id)
);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(120) NOT NULL,
    entity_type VARCHAR(120) NOT NULL,
    entity_id VARCHAR(160),
    request_id VARCHAR(120),
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id, created_at DESC);

CREATE TABLE webhook_events (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(60) NOT NULL,
    external_event_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    signature_valid BOOLEAN NOT NULL DEFAULT false,
    payload_hash VARCHAR(128) NOT NULL,
    processing_status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    error_message VARCHAR(1000),
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_webhook_event UNIQUE (provider, external_event_id)
);

-- Keep updated_at automatic for the new mutable tables.
CREATE TRIGGER update_addresses_updated_at BEFORE UPDATE ON addresses
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_auth_sessions_updated_at BEFORE UPDATE ON auth_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_carts_updated_at BEFORE UPDATE ON carts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_cart_items_updated_at BEFORE UPDATE ON cart_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_coupons_updated_at BEFORE UPDATE ON coupons
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_orders_updated_at BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_payments_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_shipments_updated_at BEFORE UPDATE ON shipments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

INSERT INTO categories (name, slug, description, active)
VALUES
    ('Saltos', 'saltos', 'Scarpins e saltos para ocasiões especiais.', true),
    ('Sapatilhas', 'sapatilhas', 'Conforto e delicadeza para todos os dias.', true),
    ('Botas', 'botas', 'Botas e coturnos para produções marcantes.', true),
    ('Sandálias', 'sandalias', 'Modelos leves, elegantes e versáteis.', true),
    ('Tênis', 'tenis', 'Tênis casuais para uma rotina confortável.', true),
    ('Mocassins', 'mocassins', 'Clássicos renovados para trabalhar e passear.', true)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO products (
    name, slug, description, short_description, base_price, compare_at_price,
    material, brand, active, featured, category_id, weight_kg, height_cm,
    width_cm, length_cm, seo_title, seo_description, published_at
)
SELECT seed.name, seed.slug, seed.description, seed.short_description,
       seed.base_price, seed.compare_at_price, seed.material, 'FemStep',
       true, seed.featured, c.id, seed.weight_kg, seed.height_cm,
       seed.width_cm, seed.length_cm, seed.name || ' | FemStep',
       seed.short_description, CURRENT_TIMESTAMP
FROM (
    VALUES
        ('Scarpin Aurora', 'scarpin-aurora', 'Scarpin de bico fino com salto médio e palmilha macia. Um modelo elegante para acompanhar produções de trabalho e eventos.', 'Elegância clássica com conforto para o dia inteiro.', 229.90, 279.90, 'Napa tecnológica', true, 'saltos', 0.650, 12.0, 18.0, 29.0),
        ('Sandália Serena', 'sandalia-serena', 'Sandália de tiras delicadas, fechamento ajustável e salto bloco estável.', 'Leveza e estabilidade em um salto bloco versátil.', 189.90, NULL, 'Material sintético', true, 'sandalias', 0.580, 11.0, 17.0, 28.0),
        ('Sapatilha Luna', 'sapatilha-luna', 'Sapatilha de bico quadrado com detalhe metálico discreto e forro macio.', 'Um clássico confortável para a rotina.', 149.90, 179.90, 'Napa tecnológica', true, 'sapatilhas', 0.420, 9.0, 16.0, 27.0),
        ('Bota Íris', 'bota-iris', 'Bota de cano curto com salto baixo, zíper lateral e acabamento minimalista.', 'Design limpo e confortável para os dias frescos.', 299.90, 349.90, 'Material tecnológico', true, 'botas', 0.950, 18.0, 22.0, 32.0),
        ('Tênis Flora', 'tenis-flora', 'Tênis casual com solado leve, recortes suaves e palmilha anatômica.', 'Conforto urbano para todos os seus passos.', 249.90, NULL, 'Multimateriais', true, 'tenis', 0.720, 13.0, 20.0, 32.0),
        ('Mocassim Alba', 'mocassim-alba', 'Mocassim com solado tratorado leve e detalhe frontal de inspiração clássica.', 'Personalidade e conforto para trabalhar ou passear.', 199.90, 229.90, 'Napa tecnológica', true, 'mocassins', 0.680, 11.0, 18.0, 29.0),
        ('Scarpin Celeste', 'scarpin-celeste', 'Scarpin slingback de salto baixo com bico fino e acabamento acetinado.', 'Delicado, moderno e fácil de combinar.', 219.90, NULL, 'Tecido acetinado', false, 'saltos', 0.520, 10.0, 17.0, 29.0),
        ('Sandália Brisa', 'sandalia-brisa', 'Sandália rasteira com tiras cruzadas e fechamento no tornozelo.', 'Frescor e praticidade para os dias de sol.', 129.90, NULL, 'Material sintético', false, 'sandalias', 0.350, 8.0, 16.0, 27.0),
        ('Sapatilha Amélie', 'sapatilha-amelie', 'Sapatilha de bico fino com laço delicado e palmilha acolchoada.', 'Feminina, delicada e confortável.', 159.90, NULL, 'Napa tecnológica', false, 'sapatilhas', 0.400, 9.0, 16.0, 27.0),
        ('Bota Siena', 'bota-siena', 'Bota de cano médio com salto bloco e acabamento fosco.', 'Uma bota marcante com caminhar seguro.', 329.90, 379.90, 'Material tecnológico', false, 'botas', 1.100, 25.0, 24.0, 34.0),
        ('Tênis Nuvem', 'tenis-nuvem', 'Tênis monocromático com espuma responsiva e cabedal respirável.', 'Sensação de leveza do começo ao fim do dia.', 269.90, 299.90, 'Tecido e material tecnológico', false, 'tenis', 0.690, 13.0, 20.0, 32.0),
        ('Mocassim Olívia', 'mocassim-olivia', 'Mocassim de linhas minimalistas com palmilha flexível e detalhe dourado.', 'Sofisticação discreta para a rotina.', 209.90, NULL, 'Napa tecnológica', false, 'mocassins', 0.620, 10.0, 18.0, 29.0)
) AS seed(
    name, slug, description, short_description, base_price, compare_at_price,
    material, featured, category_slug, weight_kg, height_cm, width_cm, length_cm
)
JOIN categories c ON c.slug = seed.category_slug
ON CONFLICT (slug) DO NOTHING;

INSERT INTO product_variants (sku, size, color, stock_quantity, reserved_quantity, active, product_id)
SELECT
    'FS-' || LPAD(p.id::TEXT, 5, '0') || '-' || sizes.size || '-' || colors.code,
    sizes.size,
    colors.name,
    8 + ((p.id + sizes.size) % 9)::INTEGER,
    0,
    true,
    p.id
FROM products p
CROSS JOIN (VALUES (33), (34), (35), (36), (37), (38), (39), (40)) AS sizes(size)
CROSS JOIN (VALUES ('Preto', 'PR'), ('Caramelo', 'CA')) AS colors(name, code)
WHERE p.brand = 'FemStep'
ON CONFLICT (product_id, size, color) DO NOTHING;

INSERT INTO product_images (url, alt_text, display_order, main_image, product_id)
SELECT
    CASE c.slug
        WHEN 'saltos' THEN 'https://images.unsplash.com/photo-1543163521-1bf539c55dd2?auto=format&fit=crop&w=1000&q=85'
        WHEN 'sapatilhas' THEN 'https://images.unsplash.com/photo-1511556532299-8f662fc26c06?auto=format&fit=crop&w=1000&q=85'
        WHEN 'botas' THEN 'https://images.unsplash.com/photo-1549298916-b41d501d3772?auto=format&fit=crop&w=1000&q=85'
        WHEN 'sandalias' THEN 'https://images.unsplash.com/photo-1603808033192-082d6919d3e1?auto=format&fit=crop&w=1000&q=85'
        WHEN 'tenis' THEN 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=1000&q=85'
        ELSE 'https://images.unsplash.com/photo-1560343090-f0409e92791a?auto=format&fit=crop&w=1000&q=85'
    END,
    p.name || ' em fundo claro',
    0,
    true,
    p.id
FROM products p
JOIN categories c ON c.id = p.category_id
WHERE p.brand = 'FemStep'
  AND NOT EXISTS (
      SELECT 1 FROM product_images pi WHERE pi.product_id = p.id AND pi.main_image = true
  );

INSERT INTO coupons (
    code, description, discount_type, discount_value, minimum_order_value,
    maximum_discount_value, usage_limit, usage_limit_per_user, starts_at, ends_at, active
)
VALUES (
    'BEMVINDA10', '10% de desconto na primeira compra', 'PERCENTAGE', 10, 99,
    80, 10000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '2 years', true
)
ON CONFLICT (code) DO NOTHING;
