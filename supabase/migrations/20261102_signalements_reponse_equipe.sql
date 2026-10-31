-- KALYNO : réponse de l'équipe visible par l'élève (écran « Mes signalements » de l'appli).
-- À passer après 20261031_signalements.sql. Script rejouable.
--
-- Deux textes distincts, volontairement :
--   * report_notes.note   -> note INTERNE de l'équipe, illisible par l'élève (script 20261101) ;
--   * reports.team_reply  -> RÉPONSE écrite pour l'élève, qu'il lit dans l'appli (ce script).

begin;

alter table public.reports
  add column if not exists team_reply text,
  add column if not exists team_replied_at timestamptz;

alter table public.reports drop constraint if exists reports_team_reply_len;
alter table public.reports
  add constraint reports_team_reply_len check (team_reply is null or char_length(team_reply) <= 1000);

-- Un élève ne peut pas s'écrire lui-même une « réponse de l'équipe » en créant un signalement.
drop policy if exists reports_insert_anon on public.reports;
create policy reports_insert_anon on public.reports
  for insert to anon
  with check (status = 'PENDING' and team_reply is null and team_replied_at is null);

drop policy if exists reports_insert_own on public.reports;
create policy reports_insert_own on public.reports
  for insert to authenticated
  with check (
    status = 'PENDING' and user_id = auth.uid()::text
    and team_reply is null and team_replied_at is null
  );

-- Lecture par l'élève : la policy reports_select_own existante couvre déjà les nouvelles colonnes.
-- Modification : seule la policy reports_admin_all (is_admin()) autorise un UPDATE ; l'élève n'en a aucune.

commit;
