wrk.method = "GET"
wrk.path = "/api/routes"
wrk.headers["Accept"] = "application/json"

response = function(status, headers, body)
  if status ~= 200 then
    io.write("non-200 status: ", status, "\n")
  end
end
