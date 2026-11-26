FROM node:22-alpine

WORKDIR /app
COPY package.json server.js backup.js admin.html ./

ENV NODE_ENV=production
# Cloud backups live here; mount a persistent volume at /data. The service runs as root so it can write to the
# volume whatever owner the platform gives it.
ENV BACKUP_DIR=/data
EXPOSE 3000

CMD ["node", "server.js"]
