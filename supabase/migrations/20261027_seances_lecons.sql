-- KALYNO : Leçon (chapitre) > Séances chronologiques > Blocs, + notes complémentaires facultatives.
-- À coller dans Supabase > SQL Editor. Script rejouable (idempotent).
-- L'app et l'admin restent fonctionnels si ce script n'est pas encore passé (ancien rendu en pile de blocs).

begin;

-- 1) Séances d'une leçon ---------------------------------------------------------------------------
-- Le type de chapter_id suit celui de chapters.id (uuid ou text), pour que la clé étrangère passe.
do $$
declare
  id_type text;
begin
  select format_type(a.atttypid, a.atttypmod) into id_type
  from pg_attribute a
  where a.attrelid = 'public.chapters'::regclass and a.attname = 'id' and not a.attisdropped;

  execute format($f$
    create table if not exists public.lesson_sessions (
      id         uuid primary key default gen_random_uuid(),
      chapter_id %s not null references public.chapters(id) on delete cascade,
      title      text not null,
      position   integer not null default 0,
      created_at timestamptz not null default now()
    )
  $f$, id_type);
end $$;

create index if not exists lesson_sessions_chapter_idx on public.lesson_sessions (chapter_id, position);

-- 2) Rattacher un bloc à une séance (null = bloc hors séance, comme avant) ---------------------------
alter table public.capsules
  add column if not exists session_id uuid references public.lesson_sessions(id) on delete set null;

create index if not exists capsules_session_idx on public.capsules (session_id);

-- 3) Notes et ressources complémentaires facultatives, rattachées à la leçon --------------------------
alter table public.chapters add column if not exists complement text;

-- 4) Sécurité : lecture publique des séances d'une leçon publiée, écriture réservée à l'admin ----------
alter table public.lesson_sessions enable row level security;

drop policy if exists lesson_sessions_read_published on public.lesson_sessions;
create policy lesson_sessions_read_published on public.lesson_sessions
  for select using (
    exists (select 1 from public.chapters c where c.id = lesson_sessions.chapter_id and c.status = 'published')
  );

drop policy if exists lesson_sessions_admin_all on public.lesson_sessions;
create policy lesson_sessions_admin_all on public.lesson_sessions
  for all to authenticated using (public.is_admin()) with check (public.is_admin());

commit;
