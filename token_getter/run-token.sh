#!/bin/bash

phantomBin=$(pwd)/phantomjs-2.1.1-linux-x86_64/bin
if [[ -d $phantomBin ]];then
	PATH=$PATH:$(pwd)/phantomjs-2.1.1-linux-x86_64/bin
fi
PATH=$PATH:$(pwd)/node_modules/.bin:

casperjs --ssl-protocol=any --ignore-ssl-errors=yes ./login-script.js
