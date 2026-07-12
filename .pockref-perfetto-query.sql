SELECT
    s.name,
    COUNT(*) AS count,
    ROUND(SUM(s.dur) / 1e6, 2) AS total_ms,
    ROUND(MAX(s.dur) / 1e6, 2) AS max_ms
FROM slice AS s
JOIN thread_track AS tt ON s.track_id = tt.id
WHERE tt.utid = 343
GROUP BY s.name
ORDER BY total_ms DESC
LIMIT 60;
