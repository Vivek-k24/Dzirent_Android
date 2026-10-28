-- Run once in Supabase > SQL Editor.
-- 1) The sign-up trigger now also saves the city typed at sign-up.
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, display_name, role, adult_confirmed, city)
  values (new.id,
          coalesce(new.raw_user_meta_data->>'display_name',''),
          coalesce(new.raw_user_meta_data->>'role','renter'),
          coalesce((new.raw_user_meta_data->>'adult_confirmed')::boolean, false),
          nullif(new.raw_user_meta_data->>'city',''));
  return new;
end; $$;

-- 2) Create profiles for any accounts that signed up before the trigger existed.
insert into public.profiles (id, display_name, role, adult_confirmed, city)
select u.id,
       coalesce(u.raw_user_meta_data->>'display_name',''),
       coalesce(u.raw_user_meta_data->>'role','renter'),
       coalesce((u.raw_user_meta_data->>'adult_confirmed')::boolean, false),
       nullif(u.raw_user_meta_data->>'city','')
from auth.users u
where not exists (select 1 from public.profiles p where p.id = u.id);
