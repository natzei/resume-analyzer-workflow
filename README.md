# Upload application form PDF

```shell
curl --request POST \
  --url 'http://localhost:9000/my-workflow/1/application-form' \
  --header 'Content-Type: application/pdf' \
  --data '<BINARY-DATA>'
```

# Upload resume PDF

```shell
curl --request POST \
  --url 'http://localhost:9000/my-workflow/1/resume' \
  --header 'Content-Type: application/pdf' \
  --data '<BINARY-DATA>'
```

# Start workflow

```shell
curl --request POST \
  --url 'http://localhost:9000/my-workflow/1/start?=' \
  --header 'Content-Type: application/pdf'
```

# Get result

```shell
curl --request GET \
  --url 'http://localhost:9000/my-workflow/1?=' \
  --header 'Content-Type: application/pdf'
```
