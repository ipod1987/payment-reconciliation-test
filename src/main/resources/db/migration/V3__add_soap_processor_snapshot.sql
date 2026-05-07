-- =============================================================================
-- V3: Add SOAP processor snapshot column for 3-way reconciliation
-- =============================================================================

ALTER TABLE reconciliation_results
    ADD COLUMN IF NOT EXISTS soap_processor_payment_snapshot JSONB;

COMMENT ON COLUMN reconciliation_results.soap_processor_payment_snapshot
    IS 'Snapshot del pago del procesador SOAP al momento de la conciliación';

COMMENT ON COLUMN reconciliation_results.status
    IS 'Estado: CONCILIATED | DISCREPANCY_AMOUNT | DISCREPANCY_DATE | MULTIPLE_DISCREPANCIES | MISSING_IN_INTERNAL | MISSING_IN_PROCESSOR | MISSING_IN_JSON_PROCESSOR | MISSING_IN_SOAP_PROCESSOR';
