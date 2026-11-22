-- KALYNO : Exercices libres (independants des disciplines, chapitres et lecons).
-- A coller dans Supabase > SQL Editor. Script rejouable. Ne touche AUCUNE table existante :
-- il cree seulement public.free_exercises avec ses propres regles RLS.

begin;

create table if not exists public.free_exercises (
  id            uuid primary key default gen_random_uuid(),
  level_id      text references public.levels(id) on update cascade on delete restrict, -- null = toutes les classes
  label         text,                                   -- etiquette libre facultative (ex. « Calcul mental »)
  kind          text not null default 'qcm',            -- qcm | flashcard | cloze (comme public.cards)
  front         text not null,
  back          text,
  choices       jsonb,
  correct_index integer,
  explanation   text,
  position      integer not null default 0,
  is_published  boolean not null default false,         -- brouillon tant que l'admin ne publie pas
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  constraint free_exercises_kind_ok  check (kind in ('qcm', 'flashcard', 'cloze')),
  constraint free_exercises_front_ok check (char_length(btrim(front)) > 0)
);

create index if not exists free_exercises_level_idx on public.free_exercises (level_id, is_published, position);

create or replace function public.free_exercises_touch_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists free_exercises_touch_updated_at on public.free_exercises;
create trigger free_exercises_touch_updated_at
  before update on public.free_exercises
  for each row execute function public.free_exercises_touch_updated_at();

alter table public.free_exercises enable row level security;

revoke all on public.free_exercises from anon, authenticated;
grant select on public.free_exercises to anon, authenticated;
grant insert, update, delete on public.free_exercises to authenticated;

-- Les eleves ne voient que les exercices publies ; les brouillons restent reserves a l'administration.
drop policy if exists free_exercises_read_published on public.free_exercises;
create policy free_exercises_read_published on public.free_exercises
  for select to anon, authenticated
  using (is_published = true);

-- is_admin() existe deja (meme regle que themes, FAQ, signalements).
drop policy if exists free_exercises_admin_all on public.free_exercises;
create policy free_exercises_admin_all on public.free_exercises
  for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

commit;
