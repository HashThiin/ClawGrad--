docker compose exec java-backend curl -s -o /dev/null -w "HTTP %{http_code}\n" --connect-timeout 5 http://47.122.119.189:18789/v1/models
