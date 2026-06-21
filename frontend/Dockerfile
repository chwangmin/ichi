# ---- base ----
FROM node:22-alpine AS base
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install

# ---- dev (docker-compose 가 사용: Vite 개발 서버 + HMR) ----
FROM base AS dev
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host"]

# ---- build (정적 산출물) ----
FROM base AS build
# Vite 는 빌드 시점에 VITE_* 를 번들에 박는다. 배포 compose 가 build args 로 주입.
# (dev 타깃은 런타임 env 로 받으므로 여기선 prod 빌드용으로만 필요)
ARG VITE_API_BASE=/api
ARG VITE_GOOGLE_MAPS_API_KEY=
ARG VITE_GOOGLE_CLIENT_ID=
ARG VITE_GOOGLE_REDIRECT_URI=
ENV VITE_API_BASE=$VITE_API_BASE \
    VITE_GOOGLE_MAPS_API_KEY=$VITE_GOOGLE_MAPS_API_KEY \
    VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID \
    VITE_GOOGLE_REDIRECT_URI=$VITE_GOOGLE_REDIRECT_URI
COPY . .
RUN npm run build

# ---- prod (정적 파일 서빙) ----
FROM nginx:alpine AS prod
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
