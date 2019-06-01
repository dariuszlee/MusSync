FROM node
COPY ./gulpfile.js /root
COPY ./package.json /root

RUN mkdir /root/src
COPY ./src /root/src

RUN ["mkdir", "/root/deploy"]
RUN ["npm", "install", "-g", "gulp-cli"]
RUN cd /root && npm install
