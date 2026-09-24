package com.costas.apkbuilder;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureVault {
  private static final String VALUE="cipher_value";
  private static final String IV="cipher_iv";
  private final SharedPreferences preferences;
  private final String alias;

  SecureVault(Context context,String storeName,String alias){
    this.preferences=context.getSharedPreferences(storeName,Context.MODE_PRIVATE);
    this.alias=alias;
  }

  void save(String value) throws Exception{
    SecretKey key=getOrCreateKey();
    Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE,key);
    byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
    preferences.edit()
      .putString(VALUE,Base64.encodeToString(encrypted,Base64.NO_WRAP))
      .putString(IV,Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
      .apply();
  }

  String read(){
    try{
      String value=preferences.getString(VALUE,null);
      String iv=preferences.getString(IV,null);
      if(value==null||iv==null)return "";
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE,getOrCreateKey(),
        new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
      return new String(cipher.doFinal(Base64.decode(value,Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }catch(Exception e){
      clear();
      return "";
    }
  }

  boolean hasValue(){return !read().isEmpty();}
  void clear(){preferences.edit().remove(VALUE).remove(IV).apply();}

  private SecretKey getOrCreateKey() throws Exception{
    KeyStore store=KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if(store.containsAlias(alias)){
      return ((KeyStore.SecretKeyEntry)store.getEntry(alias,null)).getSecretKey();
    }
    KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(alias,
      KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .build());
    return generator.generateKey();
  }
}
