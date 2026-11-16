-- Ilussioness Boutique - inventory, customer orders, reminders, calendar sync
-- and password recovery.
-- Execute manually in the Supabase SQL Editor BEFORE deploying the backend
-- (Hibernate runs with ddl-auto=validate). The script is idempotent.

-- Inventory: the product table already has name/type/stock/status columns.
-- Make sure the defaults the backend relies on are present.
ALTER TABLE public.product ALTER COLUMN stock SET DEFAULT 0;
ALTER TABLE public.product ALTER COLUMN status SET DEFAULT 'AVAILABLE';

-- Orders created from a customer (legacy orders table).
ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS customer_id uuid REFERENCES public.customer(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS status varchar(20) NOT NULL DEFAULT 'PENDING';
CREATE INDEX IF NOT EXISTS orders_customer_id_idx ON public.orders (customer_id);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'orders_status_check') THEN
        ALTER TABLE public.orders ADD CONSTRAINT orders_status_check
            CHECK (status IN ('PENDING', 'IN_PROGRESS', 'READY', 'DELIVERED', 'CANCELLED'));
    END IF;
END $$;

-- Appointment reminders and Microsoft calendar event id.
ALTER TABLE public.appointment
    ADD COLUMN IF NOT EXISTS microsoft_event_id text,
    ADD COLUMN IF NOT EXISTS reminder_sent_at timestamptz;

-- OAuth connections to Google Calendar / Microsoft Outlook (tokens AES-GCM encrypted).
CREATE TABLE IF NOT EXISTS public.calendar_connection (
    provider          varchar(20) PRIMARY KEY CHECK (provider IN ('GOOGLE', 'MICROSOFT')),
    account_email     varchar(200),
    access_token_enc  text NOT NULL,
    refresh_token_enc text,
    expires_at        timestamptz,
    connected_at      timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.calendar_connection ENABLE ROW LEVEL SECURITY;

-- Password recovery stores reset passwords as BCrypt hashes in public."user"
-- (table already present). No change needed.
