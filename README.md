##
    sudo docker run --name pgsql \
          -e POSTGRES_USER=postgres \
          -e POSTGRES_PASSWORD=admin1234 \
          -e POSTGRES_DB=demo_db \
          -v postgresdb_data:/data/demo_db \
          -p 5432:5432 \
          -d postgres:18.4

## read the file logs 
    Success. You can now start the database server using:
    pg_ctl -D /var/lib/postgresql/18/docker -l logfile start