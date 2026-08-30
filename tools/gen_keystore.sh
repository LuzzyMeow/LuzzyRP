#!/usr/bin/env bash
# LuzzyRP 发布签名密钥生成（v0.1.0 已生成，此脚本用于丢失后重建的记录）
keytool -genkeypair -v -keystore keystore/luzzy-release.keystore -alias luzzy \
  -keyalg RSA -keysize 4096 -validity 10950 \
  -storepass "LuzzyRP2026!" -keypass "LuzzyRP2026!" \
  -dname "CN=LuzzyRP, OU=LuzzyMeow, O=LuzzyMeow, L=Internet, C=CN"
