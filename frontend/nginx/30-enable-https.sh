#!/bin/sh
# Enables the HTTPS server block when a TLS certificate is mounted; otherwise only HTTP is served.
set -e
if [ -f /etc/nginx/certs/tls.crt ] && [ -f /etc/nginx/certs/tls.key ]; then
    cp /etc/nginx/https.conf.template /etc/nginx/conf.d/https.conf
    echo "30-enable-https: certificate found, HTTPS enabled on port 8443"
else
    rm -f /etc/nginx/conf.d/https.conf
    echo "30-enable-https: no certificate mounted, HTTPS disabled"
fi
