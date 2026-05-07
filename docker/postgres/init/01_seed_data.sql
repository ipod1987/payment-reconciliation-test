-- =============================================================================
-- Datos de prueba para desarrollo local
-- Este script se ejecuta UNA sola vez al crear el contenedor (docker-entrypoint-initdb.d)
-- =============================================================================

-- Flyway crea las tablas; este script solo inserta datos de demo.
-- Si Flyway no ha corrido aún al momento que Docker ejecuta este script,
-- no hay problema: el INSERT fallará silenciosamente y los datos se
-- pueden insertar manualmente con: make seed (ver Makefile)

INSERT INTO internal_payments (payment_id, amount, currency, status, description, transaction_date)
VALUES

  -- -------------------------------------------------------------------------
  -- CONCILIATED: monto y fecha coinciden con el procesador (tolerancia ±5 min)
  -- -------------------------------------------------------------------------
  ('pay_abc123',  100.00,   'USD', 'APPROVED', 'E-commerce purchase - Nike Store',       '2024-06-15 10:00:00'),
  ('pay_c001',     50.00,   'USD', 'APPROVED', 'Coffee shop - Starbucks',                '2024-06-15 14:00:00'),
  ('pay_c002',    199.99,   'EUR', 'APPROVED', 'Online marketplace - Amazon',            '2024-06-16 09:30:00'),
  ('pay_c003',   1500.00,   'USD', 'APPROVED', 'Flight booking - Delta Airlines',        '2024-06-16 15:00:00'),
  ('pay_c004',     85.00,   'GBP', 'APPROVED', 'Restaurant - The Fat Duck',              '2024-06-17 12:00:00'),
  ('pay_c005',     29.99,   'USD', 'APPROVED', 'Streaming subscription - Netflix',       '2024-06-17 08:00:00'),

  -- -------------------------------------------------------------------------
  -- DISCREPANCY_AMOUNT: monto difiere > 0.01 entre interno y procesador
  -- -------------------------------------------------------------------------
  ('pay_disc001', 100.00,   'USD', 'APPROVED', 'Subscription - Spotify Premium',         '2024-06-15 11:00:00'),
  ('pay_d001',    500.00,   'USD', 'APPROVED', 'Wholesale order - Sysco Foods',          '2024-06-18 08:00:00'),
  ('pay_d002',   1200.00,   'USD', 'APPROVED', 'Electronics - Apple Store',              '2024-06-18 11:00:00'),
  ('pay_d003',     75.00,   'EUR', 'APPROVED', 'Medical services - Dr. Martinez',        '2024-06-18 16:00:00'),

  -- -------------------------------------------------------------------------
  -- DISCREPANCY_DATE: fecha difiere > 5 min entre interno y procesador
  -- -------------------------------------------------------------------------
  ('pay_date001',  75.50,   'EUR', 'APPROVED', 'Hotel booking - Marriott',               '2024-06-15 09:00:00'),
  ('pay_dt001',   320.00,   'USD', 'APPROVED', 'Car rental - Hertz',                     '2024-06-19 10:00:00'),
  ('pay_dt002',   640.00,   'USD', 'APPROVED', 'Consulting services - Accenture',        '2024-06-19 14:00:00'),

  -- -------------------------------------------------------------------------
  -- MULTIPLE_DISCREPANCIES: monto Y fecha difieren simultáneamente
  -- -------------------------------------------------------------------------
  ('pay_multi001', 300.00,  'USD', 'APPROVED', 'SaaS platform - Salesforce',             '2024-06-20 11:00:00'),
  ('pay_multi002', 150.00,  'EUR', 'APPROVED', 'Logistics - DHL Express',                '2024-06-20 09:00:00'),

  -- -------------------------------------------------------------------------
  -- MISSING_IN_PROCESSOR: existe internamente, el procesador devuelve 404
  -- -------------------------------------------------------------------------
  ('pay_missing',  250.00,  'USD', 'APPROVED', 'Wire transfer - International',          '2024-06-15 12:00:00'),
  ('pay_mp001',    420.00,  'USD', 'PENDING',  'Marketplace payout - Shopify',           '2024-06-21 13:00:00'),
  ('pay_mp002',     85.00,  'EUR', 'APPROVED', 'Freelance payment - Upwork',             '2024-06-21 17:00:00')

ON CONFLICT (payment_id) DO NOTHING;
