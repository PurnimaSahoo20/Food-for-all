## PULL AND RUN A MYSQL INSTANCE FROM DOCKER

```
docker run -d --name my-mysql -e MYSQL_ROOT_PASSWORD=realtiger -e MYSQL_DATABASE=spring -p 3306:3306 mysql:8.0
```