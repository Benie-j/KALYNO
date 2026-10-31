-- KALYNO : notes internes de l'équipe sur les signalements (espace admin uniquement).
-- Table séparée de `reports` exprès : un élève connecté peut lire SES signalements, il ne doit jamais
-- voir les notes de l'équipe. Ici, seul is_admin() a accès. Script rejouable. À passer après 20261031.

begin;

create table if not exists public.report_notes (
  report_id  uuid primary key references public.reports (id),
  note       text not null default '' check (char_length(note) <= 2000),
  updated_at timestamptz not null default now(),
  updated_by uuid default auth.uid()
);

alter table public.report_notes enable row level security;

revoke all on public.report_notes from anon;
grant select, insert, update on public.report_notes to authenticated;

drop policy if exists report_notes_admin_all on public.report_notes;
create policy report_notes_admin_all on public.report_notes
  for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

commit;

-- Pas de DELETE : pour effacer une note, l'espace admin enregistre un texte vide.
