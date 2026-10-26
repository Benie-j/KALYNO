-- KALYNO : le premier défi (kind = 'discovery') adopte les types d'exercices du Dojo.
-- Une bonne réponse, plusieurs bonnes réponses, ou saisie libre, avec indice et explication.
-- À coller dans Supabase > SQL Editor. Script rejouable (idempotent).
-- L'app reste fonctionnelle même si ce script n'est pas encore passé : elle lit alors l'ancien format.

begin;

-- 1) Colonnes des questions ----------------------------------------------------------------------
alter table public.questions
  add column if not exists kind            text not null default 'single',
  add column if not exists correct_indices integer[],
  add column if not exists answer          text,
  add column if not exists hint            text;

-- Une saisie libre n'a ni choix ni indice de bonne réponse.
alter table public.questions alter column correct_index drop not null;

-- 2) Retirer les anciennes contraintes « 4 choix exactement » / « correct_index entre 0 et 3 » ----
do $$
declare
  c record;
begin
  for c in
    select conname
    from pg_constraint
    where conrelid = 'public.questions'::regclass
      and contype = 'c'
      and (pg_get_constraintdef(oid) ilike '%choices%' or pg_get_constraintdef(oid) ilike '%correct_index%')
  loop
    execute format('alter table public.questions drop constraint %I', c.conname);
  end loop;
end $$;

alter table public.questions drop constraint if exists questions_kind_check;
alter table public.questions
  add constraint questions_kind_check check (kind in ('single', 'multiple', 'text'));

-- 3) Reprise des questions déjà publiées : elles deviennent des « une bonne réponse » ------------
update public.questions
   set correct_indices = array[correct_index]
 where correct_indices is null
   and correct_index is not null;

commit;

-- Lecture publique : inchangée. Les questions d'un défi publié restent lisibles par l'app,
-- les brouillons restent réservés à l'administration (politiques RLS existantes).
