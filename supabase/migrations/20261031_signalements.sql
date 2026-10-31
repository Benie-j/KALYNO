-- KALYNO : signalements d'erreurs envoyés par les élèves (leçon, exercice, paramètres).
-- À coller dans Supabase > SQL Editor. Script rejouable (idempotent).
-- L'app reste fonctionnelle même si ce script n'est pas encore passé : les signalements restent
-- dans Room (table locale `reports`) et partent dès que la table existe. Rien n'est jamais supprimé.

begin;

-- 1) Table ---------------------------------------------------------------------------------------
create table if not exists public.reports (
  id            uuid primary key default gen_random_uuid(),
  user_id       text not null,                      -- id du compte, ou id local anonyme d'un invité
  source_type   text not null check (source_type in ('LESSON', 'EXERCISE', 'SETTINGS')),
  source_id     text,                               -- leçon / carte du Dojo / question de bienvenue ; null pour les paramètres
  category      text not null,
  description   text not null default '',
  context_label text,                               -- libellé lisible (« Maths › Fractions »), voir note ci-dessous
  status        text not null default 'PENDING' check (status in ('PENDING', 'IN_REVIEW', 'RESOLVED', 'REJECTED')),
  created_at    timestamptz not null default now(),
  constraint reports_description_len check (char_length(description) <= 1000),
  constraint reports_context_len check (context_label is null or char_length(context_label) <= 300),
  constraint reports_user_len check (char_length(user_id) between 1 and 64)
);

create index if not exists reports_status_created_idx on public.reports (status, created_at desc);
create index if not exists reports_source_idx on public.reports (source_type, source_id);
create index if not exists reports_user_idx on public.reports (user_id);

-- 2) Sécurité (RLS) ------------------------------------------------------------------------------
alter table public.reports enable row level security;

-- Droits de base : un invité (rôle anon) ne peut qu'INSÉRER ; un compte connecté lit ses propres rapports.
revoke all on public.reports from anon;
grant insert on public.reports to anon;
grant select, insert, update on public.reports to authenticated;

-- Invité (pas de session) : insertion seule, toujours en statut PENDING.
drop policy if exists reports_insert_anon on public.reports;
create policy reports_insert_anon on public.reports
  for insert to anon
  with check (status = 'PENDING');

-- Compte connecté : insertion à son propre nom seulement.
drop policy if exists reports_insert_own on public.reports;
create policy reports_insert_own on public.reports
  for insert to authenticated
  with check (status = 'PENDING' and user_id = auth.uid()::text);

-- Compte connecté : lecture de ses propres signalements.
drop policy if exists reports_select_own on public.reports;
create policy reports_select_own on public.reports
  for select to authenticated
  using (user_id = auth.uid()::text);

-- Administration : lecture et changement de statut (is_admin() existe déjà dans tes autres scripts).
drop policy if exists reports_admin_all on public.reports;
create policy reports_admin_all on public.reports
  for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

commit;

-- Pas de DELETE accordé à l'app : un signalement se clôture en passant `status` à RESOLVED ou REJECTED.
-- Note : `context_label` est une colonne en plus de la spécification de départ. Elle sert à l'équipe à
-- retrouver l'élément signalé sans jointure. Pour la retirer, enlève aussi la ligne "context_label" de
-- ReportRepository.body() : PostgREST refuse une colonne inconnue.
