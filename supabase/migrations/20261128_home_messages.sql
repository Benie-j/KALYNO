-- KALYNO : messages du coach de l'Accueil (administrables à distance).
-- À coller dans Supabase > SQL Editor. Script rejouable (idempotent).
-- L'app reste fonctionnelle même si ce script n'est pas encore passé : l'Accueil n'affiche
-- simplement pas de message, puis le message du jour apparaît dès la première synchronisation.
-- Aucun contenu n'est écrit ici : les messages se publient depuis l'admin.

begin;

-- 1) Table ---------------------------------------------------------------------------------------
create table if not exists public.home_messages (
  id          uuid primary key default gen_random_uuid(),
  level_id    text,                                  -- null = toutes les classes
  message     text not null,
  position    integer not null default 0,            -- ordre de rotation : un message par jour, en boucle
  is_active   boolean not null default true,
  updated_at  timestamptz not null default now(),
  constraint home_messages_message_len check (char_length(btrim(message)) between 1 and 240),
  constraint home_messages_position_pos check (position >= 0)
);

create index if not exists home_messages_order_idx on public.home_messages (position, id);
create index if not exists home_messages_level_idx on public.home_messages (level_id);

-- 2) updated_at automatique ----------------------------------------------------------------------
create or replace function public.home_messages_touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists home_messages_touch_updated_at on public.home_messages;
create trigger home_messages_touch_updated_at
  before insert or update on public.home_messages
  for each row execute function public.home_messages_touch_updated_at();

-- 3) Sécurité (RLS) ------------------------------------------------------------------------------
alter table public.home_messages enable row level security;

-- Lecture publique des messages actifs (invité ou connecté) ; écriture réservée à l'administration.
revoke all on public.home_messages from anon, authenticated;
grant select on public.home_messages to anon, authenticated;
grant insert, update, delete on public.home_messages to authenticated;

drop policy if exists home_messages_read_active on public.home_messages;
create policy home_messages_read_active on public.home_messages
  for select to anon, authenticated
  using (is_active);

-- is_admin() existe déjà dans les autres scripts (même règle que faq_items, reports, séances…).
drop policy if exists home_messages_admin_all on public.home_messages;
create policy home_messages_admin_all on public.home_messages
  for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

commit;
