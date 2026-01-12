FROM node:18-alpine

WORKDIR /app

COPY package*.json ./
RUN npm install --production

COPY src/ ./src/
COPY deploy/ ./deploy/

CMD ["node", "src/index.js"]
