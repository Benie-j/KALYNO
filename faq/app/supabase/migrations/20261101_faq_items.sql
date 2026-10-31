-- KALYNO : Centre d'aide & FAQ dynamique (questions / réponses administrables à distance).
-- À coller dans Supabase > SQL Editor. Script rejouable (idempotent).
-- L'app reste fonctionnelle même si ce script n'est pas encore passé : le Centre d'aide affiche
-- simplement son état vide, puis se remplit à la première synchronisation réussie.
-- Aucun contenu n'est écrit ici : la FAQ se remplit depuis l'admin (section « Centre d'aide · FAQ »).

begin;

-- 1) Table ---------------------------------------------------------------------------------------
create table if not exists public.faq_items (
  id          uuid primary key default gen_random_uuid(),
  category    text not null,                       -- libellé affiché comme titre de groupe (« Compte », « Dojo »…)
  question    text not null,
  answer      text not null,
  order_index integer not null default 0,          -- ordre global : les catégories apparaissent dans l'ordre de leur 1re question
  updated_at  timestamptz not null default now(),  -- tenu à jour par le déclencheur ci-dessous
  constraint faq_items_category_len check (char_length(btrim(category)) between 1 and 80),
  constraint faq_items_question_len check (char_length(btrim(question)) between 1 and 300),
  constraint faq_items_answer_len   check (char_length(btrim(answer))   between 1 and 5000),
  constraint faq_items_order_pos    check (order_index >= 0)
);

create index if not exists faq_items_order_idx    on public.faq_items (order_index, question);
create index if not exists faq_items_category_idx on public.faq_items (category);

-- 2) updated_at automatique ----------------------------------------------------------------------
create or replace function public.faq_items_touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists faq_items_touch_updated_at on public.faq_items;
create trigger faq_items_touch_updated_at
  before insert or update on public.faq_items
  for each row execute function public.faq_items_touch_updated_at();

-- 3) Sécurité (RLS) ------------------------------------------------------------------------------
alter table public.faq_items enable row level security;

-- Lecture publique (invité ou connecté) ; écriture réservée à l'administration.
revoke all on public.faq_items from anon, authenticated;
grant select on public.faq_items to anon, authenticated;
grant insert, update, delete on public.faq_items to authenticated;

drop policy if exists faq_items_read_all on public.faq_items;
create policy faq_items_read_all on public.faq_items
  for select to anon, authenticated
  using (true);

-- is_admin() existe déjà dans tes autres scripts (même règle que themes, reports, séances…).
drop policy if exists faq_items_admin_all on public.faq_items;
create policy faq_items_admin_all on public.faq_items
  for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

commit;

-- Exemple pour tester sans passer par l'admin (à décommenter puis exécuter une fois) :
-- insert into public.faq_items (category, question, answer, order_index) values
--   ('Démarrer', 'Qu''est-ce que l''Active Recall ?', 'C''est se forcer à retrouver la réponse de mémoire plutôt que de relire sa leçon.', 0),
--   ('Démarrer', 'À quoi sert le SRS ?', 'Il te représente chaque notion juste avant que tu l''oublies.', 1),
--   ('Compte',   'Mes progrès sont-ils sauvegardés ?', 'Oui, dès que tu te connectes avec Google ou ton e-mail.', 2);
