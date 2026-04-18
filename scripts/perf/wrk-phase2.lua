init = function(args)
  requests = 0
end

request = function()
  requests = requests + 1
  if requests % 10 == 0 then
    return wrk.format("POST", "/api/routes/publish", {["Content-Type"]="application/json"})
  end
  return wrk.format("GET", "/api/routes")
end

response = function(status, headers, body)
  if status ~= 200 then
    io.write("unexpected status: ", status, "\n")
  end
end
