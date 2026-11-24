import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  SafeAreaView,
  ScrollView,
  StatusBar,
  ActivityIndicator,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { authApi } from '../services/api';
import ChaveRecuperacao, { abrirPaginaDoSite } from '../components/ChaveRecuperacao';

import { voltar } from '../utils/voltar';
export default function CadastroScreen() {
  const navigation = useNavigation<any>();
  
  const [nome, setNome] = useState<string>('');
  const [email, setEmail] = useState<string>('');
  const [senha, setSenha] = useState<string>('');
  const [confirmarSenha, setConfirmarSenha] = useState<string>('');

  const [erro, setErro] = useState<string>('');
  const [carregando, setCarregando] = useState<boolean>(false);
  const [aceite, setAceite] = useState<boolean>(false);
  const [chave, setChave] = useState<string | null>(null);

  const irParaInicio = () => navigation.reset({ index: 0, routes: [{ name: 'Home' }] });

  const handleCadastro = async () => {
    if (carregando) return;

    const nomeLimpo = nome.trim();
    const emailLimpo = email.trim().toLowerCase();

    if (!nomeLimpo || !emailLimpo || !senha) {
      setErro('Preencha nome, e-mail e senha.');
      return;
    }
    if (!/^\S+@\S+\.\S+$/.test(emailLimpo)) {
      setErro('Digite um e-mail válido.');
      return;
    }
    if (senha.length < 6) {
      setErro('A senha deve ter no mínimo 6 caracteres.');
      return;
    }
    if (senha !== confirmarSenha) {
      setErro('As senhas não conferem.');
      return;
    }
    if (!aceite) {
      setErro('Para criar a conta, aceite os Termos de Uso e a Política de Privacidade.');
      return;
    }

    try {
      setCarregando(true);
      setErro('');
      // Cria a conta e já entra com ela
      const { recoveryKey } = await authApi.register(nomeLimpo, emailLimpo, senha);
      if (recoveryKey) setChave(recoveryKey);
      else irParaInicio();
    } catch (e: any) {
      setErro(e?.message ?? 'Não foi possível criar a conta.');
    } finally {
      setCarregando(false);
    }
  };

  if (chave) {
    return (
      <SafeAreaView style={styles.safeArea}>
        <ScrollView contentContainerStyle={{ flexGrow: 1, justifyContent: 'center', padding: 20, backgroundColor: '#F1F4FA' }}>
          <ChaveRecuperacao
            chave={chave}
            email={email.trim().toLowerCase()}
            titulo="Conta criada! Guarde sua chave"
            textoBotao="Começar a estudar"
            onConcluir={irParaInicio}
          />
        </ScrollView>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor="#2563EB" />
      
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={styles.container}
      >
        <ScrollView contentContainerStyle={styles.scrollContainer} bounces={false}>
          
          {/* Cabeçalho */}
          <View style={styles.header}>
            {/* Ícone de voltar simulado (pode usar react-native-vector-icons) */}
            <TouchableOpacity onPress={() => voltar(navigation, 'Login')} style={styles.backButton}>
              <Text style={styles.backButtonText}>{'<'}</Text>
            </TouchableOpacity>
            
            <Text style={styles.title}>Criar conta</Text>
            <Text style={styles.subtitle}>
              Comece sua jornada no Trilha+
            </Text>
          </View>

          {/* Formulário */}
          <View style={styles.formContainer}>
            
            <View style={styles.inputGroup}>
              <Text style={styles.label}>Nome completo</Text>
              <TextInput
                style={styles.input}
                placeholder="Seu nome"
                placeholderTextColor="#A0AEC0"
                value={nome}
                onChangeText={setNome}
              />
            </View>

            <View style={styles.inputGroup}>
              <Text style={styles.label}>E-mail</Text>
              <TextInput
                style={styles.input}
                placeholder="seu@email.com"
                placeholderTextColor="#A0AEC0"
                keyboardType="email-address"
                autoCapitalize="none"
                value={email}
                onChangeText={setEmail}
              />
            </View>

            <View style={styles.inputGroup}>
              <Text style={styles.label}>Senha</Text>
              <TextInput
                style={styles.input}
                placeholder="••••••••"
                placeholderTextColor="#A0AEC0"
                secureTextEntry
                value={senha}
                onChangeText={setSenha}
              />
            </View>

            <View style={styles.inputGroup}>
              <Text style={styles.label}>Confirmar senha</Text>
              <TextInput
                style={styles.input}
                placeholder="••••••••"
                placeholderTextColor="#A0AEC0"
                secureTextEntry
                value={confirmarSenha}
                onChangeText={setConfirmarSenha}
              />
            </View>

            <TouchableOpacity
              style={styles.aceiteLinha}
              onPress={() => setAceite((v) => !v)}
              activeOpacity={0.8}
              accessibilityRole="checkbox"
              accessibilityState={{ checked: aceite }}
            >
              <View style={[styles.check, aceite && styles.checkOn]}>
                {aceite ? <Text style={styles.checkV}>✓</Text> : null}
              </View>
              <Text style={styles.aceiteTexto}>
                Li e aceito os{' '}
                <Text style={styles.linkText} onPress={() => abrirPaginaDoSite('/termos')}>Termos de Uso</Text>
                {' '}e a{' '}
                <Text style={styles.linkText} onPress={() => abrirPaginaDoSite('/privacidade')}>Política de Privacidade</Text>
                . Tenho 18 anos ou mais, ou tenho autorização do meu responsável legal.
              </Text>
            </TouchableOpacity>

            {erro ? <Text style={styles.erroTexto}>{erro}</Text> : null}

            <TouchableOpacity
              style={[styles.button, carregando && { opacity: 0.7 }]}
              onPress={handleCadastro}
              activeOpacity={0.8}
              disabled={carregando}
            >
              {carregando ? (
                <ActivityIndicator color="#FFFFFF" />
              ) : (
                <Text style={styles.buttonText}>Criar conta</Text>
              )}
            </TouchableOpacity>

            <Text style={styles.termsText}>
              Já tem conta?{' '}
              <Text style={styles.linkText} onPress={() => navigation.navigate('Login')}>Entrar</Text>
            </Text>
            
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  aceiteLinha: { flexDirection: 'row', alignItems: 'flex-start', marginBottom: 16 },
  check: { width: 22, height: 22, borderRadius: 6, borderWidth: 2, borderColor: '#94A3B8', alignItems: 'center', justifyContent: 'center', marginRight: 10, marginTop: 1 },
  checkOn: { backgroundColor: '#2563EB', borderColor: '#2563EB' },
  checkV: { color: '#FFFFFF', fontWeight: '900', fontSize: 14 },
  aceiteTexto: { flex: 1, fontSize: 13, color: '#4A5568', lineHeight: 19 },
  erroTexto: {
    color: '#DC2626',
    fontSize: 13,
    fontWeight: '600',
    marginBottom: 12,
  },
  safeArea: {
    flex: 1,
    backgroundColor: '#2563EB', 
  },
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
  },
  scrollContainer: {
    flexGrow: 1,
  },
  header: {
    backgroundColor: '#2563EB',
    paddingHorizontal: 24,
    paddingTop: 20,
    paddingBottom: 40,
  },
  backButton: {
    position: 'absolute',
    top: 0,
    left: 20,
    zIndex: 10,
    padding: 8,
    paddingLeft: 10,
  },
  backButtonText: {
    color: '#FFFFFF',
    backgroundColor: '#fefdfd6d',
    borderRadius: 15,
    width: 30,
    paddingLeft: 6,
    paddingBottom: 2,
    fontSize: 24,
    fontWeight: 'bold',
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#FFFFFF',
    marginTop: 20,
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#E2E8F0',
  },
  formContainer: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 24,
    paddingTop: 32,
    paddingBottom: 24,
  },
  inputGroup: {
    marginBottom: 20,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: '#2D3748',
    marginBottom: 8,
  },
  input: {
    backgroundColor: '#F8FAFC',
    borderWidth: 1,
    borderColor: '#E2E8F0',
    borderRadius: 12,
    padding: 16,
    fontSize: 16,
    color: '#2D3748',
  },
  button: {
    backgroundColor: '#EA580C', 
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    marginTop: 12,
    marginBottom: 24,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: 'bold',
  },
  termsText: {
    textAlign: 'center',
    fontSize: 14,
    color: '#718096',
    lineHeight: 20,
  },
  linkText: {
    color: '#2563EB', 
    fontWeight: 'bold',
  },
});