package com.hanma.echocore;

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

public class SecurePrefs {
    private static final String ALIAS="echocore_omega_model_key";
    private static final String PREF="echocore_omega_settings";
    private final SharedPreferences prefs;

    public SecurePrefs(Context context){prefs=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);}

    public void put(String key,String value){prefs.edit().putString(key,value==null?"":value).apply();}
    public String get(String key,String fallback){try{return prefs.getString(key,fallback);}catch(ClassCastException e){return fallback;}}
    public void putBool(String key,boolean value){prefs.edit().putBoolean(key,value).apply();}
    public boolean getBool(String key,boolean fallback){try{return prefs.getBoolean(key,fallback);}catch(ClassCastException e){return fallback;}}
    public void putInt(String key,int value){prefs.edit().putInt(key,value).apply();}
    public int getInt(String key,int fallback){try{return prefs.getInt(key,fallback);}catch(ClassCastException e){return fallback;}}
    public void putLong(String key,long value){prefs.edit().putLong(key,value).apply();}
    public long getLong(String key,long fallback){try{return prefs.getLong(key,fallback);}catch(ClassCastException e){return fallback;}}
    public boolean contains(String key){return prefs.contains(key);}
    public void remove(String key){prefs.edit().remove(key).apply();}

    public void putSecret(String key,String value){
        try{
            SecretKey secret=getOrCreateKey();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,secret);
            byte[] encrypted=cipher.doFinal((value==null?"":value).getBytes(StandardCharsets.UTF_8));
            String payload=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP);
            prefs.edit().putString(key,payload).apply();
        }catch(Exception e){prefs.edit().remove(key).apply();}
    }

    public String getSecret(String key){
        String payload;
        try{payload=prefs.getString(key,"");}catch(ClassCastException e){return "";}
        if(payload==null||payload.isEmpty())return "";
        try{
            String[] p=payload.split(":",2);if(p.length!=2)return "";
            SecretKey secret=getOrCreateKey();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,secret,new GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(p[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
        }catch(Exception e){return "";}
    }

    private SecretKey getOrCreateKey() throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(ALIAS)){
            try{return ((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null)).getSecretKey();}
            catch(Exception badKey){try{ks.deleteEntry(ALIAS);}catch(Exception ignored){}}
        }
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return kg.generateKey();
    }
}
