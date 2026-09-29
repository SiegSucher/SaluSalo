-- Salu-Salo: Storage files that nothing in the database uses (audit #24). READ-ONLY.
--
-- Run it in the Supabase SQL Editor (role "postgres"). It deletes nothing. It
-- lists files older than one day (a post in progress uploads its photos a
-- moment before its listing is saved) that are:
--   * in food-images, and not the cover or an extra photo of any listing
--     (removed listings keep theirs: their reservations still show them);
--   * in profile-images, and not any profile's current photo.
-- Typical sources: a post whose answer was lost and that was never retried, a
-- listing deleted while its photo delete failed, an old profile photo whose
-- delete failed. Review the list; to remove a file, delete it in Storage
-- (dashboard -> Storage -> the bucket -> the file). Nothing cleans them up
-- automatically, on purpose: a wrong automatic delete would lose real photos.

select o.bucket_id,
       o.name,
       o.created_at,
       pg_size_pretty(coalesce((o.metadata ->> 'size')::bigint, 0)) as size
from storage.objects o
where o.created_at < now() - interval '1 day'
  and o.name not like '%.emptyFolderPlaceholder'
  and (
    (o.bucket_id = 'food-images'
     and not exists (select 1 from public.food_posts p
                     where p.image_url = o.name or o.name = any(p.extra_image_paths)))
    or
    (o.bucket_id = 'profile-images'
     and not exists (select 1 from public.users u where u.profile_image_url = o.name))
  )
order by o.bucket_id, o.created_at;
