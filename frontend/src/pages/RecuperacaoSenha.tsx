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
  ActivityIndicator,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { authApi } from '../services/api';
import ChaveRecuperacao from '../components/ChaveRecuperacao';

// Troca de senha sem e-mail: a pessoa informa o e-mail, a chave de recuperação
// (recebida no cadastro ou gerada no Perfil) e a senha nova. O servidor devolve
// uma chave nova, que substitui a antiga.
export default function RecuperacaoSenha() {
  const navigation = useNavigation<any>();
  const [email, setEmail] = useState<string>('');
  const [chave, setChave] = useState<string>('');
  const [novaSenha, setNovaSenha] = useState<string>('');
  const [confirmarSenha, setConfirmarSenha] = useState<string>('');
  const [erro, setErro] = useState<string>('');
  const [carregando, setCarregando] = useState<boolean>(false);
  const [chaveNova, setChaveNova] = useState<string | null>(null);

  const emailLimpo = email.trim().toLowerCase();

  const irParaLogin = () => navigation.reset({ index: 0, routes: [{ name: 'Login' }] });

  const handleNovaSenha = async () => {
    if (carregando) return;
    if (!/^\S+@\S+\.\S+$/.test(emailLimpo)) {
      setErro('Digite um e-mail válido.');
      return;
    }
    if (chave.replace(/[\s-]/g, '').length < 8) {
      setErro('Digite a chave de recuperação (ex.: TRLH-ABCD-EFGH-JKLM).');
      return;
    }
    if (novaSenha.length < 6) {
      setErro('A senha deve ter no mínimo 6 caracteres.');
      return;
    }
    if (novaSenha !== confirmarSenha) {
      setErro('As senhas não conferem.');
      return;
    }

    try {
      setCarregando(true);
      setErro('');
      const res = await authApi.resetPasswordWithKey(emailLimpo, chave.trim().toUpperCase(), novaSenha);
      if (res?.recoveryKey) setChaveNova(res.recoveryKey);
      else irParaLogin();
    } catch (e: any) {
      setErro(e?.message ?? 'Não foi possível alterar a senha.');
    } finally {
      setCarregando(false);
    }
  };

  if (chaveNova) {
    return (
      <SafeAreaView style={styles.safeArea}>
        <ScrollView contentContainerStyle={{ flexGrow: 1, justifyContent: 'center', padding: 20 }}>
          <ChaveRecuperacao
            chave={chaveNova}
            email={emailLimpo}
            titulo="Senha alterada! Guarde a chave nova"
            textoBotao="Entrar com a nova senha"
            onConcluir={irParaLogin}
          />
        </ScrollView>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safeArea}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={styles.container}
      >
        <ScrollView contentContainerStyle={{ flexGrow: 1 }} keyboardShouldPersistTaps="handled">
          <View style={styles.header}>
            <TouchableOpacity onPress={() => (navigation.canGoBack() ? navigation.goBack() : irParaLogin())} style={styles.backButton}>
              <Text style={styles.backButtonText}>{'<'}</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.content}>
            <Text style={styles.title}>Criar nova senha</Text>
            <Text style={styles.subtitle}>
              Digite seu e-mail e a chave de recuperação que apareceu quando você criou a conta.
            </Text>

            <View style={styles.form}>
              <TextInput
                style={styles.input}
                placeholder="E-mail"
                placeholderTextColor="#888"
                keyboardType="email-address"
                autoCapitalize="none"
                autoCorrect={false}
                value={email}
                onChangeText={setEmail}
              />
              <TextInput
                style={[styles.input, styles.inputChave]}
                placeholder="Chave: TRLH-XXXX-XXXX-XXXX"
                placeholderTextColor="#888"
                autoCapitalize="characters"
                autoCorrect={false}
                value={chave}
                onChangeText={setChave}
              />
              <TextInput
                style={styles.input}
                placeholder="Nova senha (mín. 6 caracteres)"
                placeholderTextColor="#888"
                secureTextEntry
                value={novaSenha}
                onChangeText={setNovaSenha}
              />
              <TextInput
                style={styles.input}
                placeholder="Confirmar nova senha"
                placeholderTextColor="#888"
                secureTextEntry
                value={confirmarSenha}
                onChangeText={setConfirmarSenha}
                onSubmitEditing={handleNovaSenha}
              />

              {erro ? <Text style={styles.erroTexto}>{erro}</Text> : null}

              <TouchableOpacity
                style={[styles.button, carregando && { opacity: 0.7 }]}
                onPress={handleNovaSenha}
                activeOpacity={0.8}
                disabled={carregando}
              >
                {carregando ? <ActivityIndicator color="#FFFFFF" /> : <Text style={styles.buttonText}>Salvar nova senha</Text>}
              </TouchableOpacity>

              <View style={styles.ajuda}>
                <Text style={styles.ajudaTitulo}>Não tem a chave?</Text>
                <Text style={styles.ajudaTexto}>
                  Se você ainda está conectado em algum aparelho, abra Perfil → Chave de recuperação e gere
                  uma nova. Por segurança, sem a chave ou a senha atual não dá para trocar a senha.
                </Text>
              </View>
            </View>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  inputChave: { letterSpacing: 1, fontWeight: '700' },
  ajuda: { marginTop: 20, backgroundColor: '#EEF2FF', borderRadius: 12, padding: 14 },
  ajudaTitulo: { fontWeight: '700', color: '#2D3748', marginBottom: 4 },
  ajudaTexto: { color: '#4A5568', fontSize: 13, lineHeight: 19 },
  erroTexto: {
    color: '#DC2626',
    fontSize: 13,
    fontWeight: '600',
    marginTop: -8,
    marginBottom: 16,
  },
  reenviar: {
    alignItems: 'center',
    paddingVertical: 16,
  },
  reenviarTexto: {
    color: '#4299E1',
    fontSize: 14,
    fontWeight: '600',
  },
  safeArea: {
    flex: 1,
    backgroundColor: '#F8F9FA',
  },
  container: {
    flex: 1,
  },
  header: {
    paddingHorizontal: 24,
    paddingTop: 16,
    paddingBottom: 16,
  },
  backButton: {
    padding: 8,
    alignSelf: 'flex-start',
    marginLeft: -8, // Ajuste fino para alinhar com o texto abaixo
  },
  backButtonText: {
    color: '#2D3748',
    fontSize: 24,
    fontWeight: 'bold',
  },
  content: {
    flex: 1,
    paddingHorizontal: 24,
    justifyContent: 'center',
    paddingBottom: 40,
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#2D3748',
    marginBottom: 12,
  },
  subtitle: {
    fontSize: 16,
    color: '#718096',
    marginBottom: 32,
    lineHeight: 24,
  },
  form: {
    width: '100%',
  },
  input: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E2E8F0',
    borderRadius: 12,
    padding: 16,
    fontSize: 16,
    color: '#2D3748',
    marginBottom: 16,
  },
  button: {
    backgroundColor: '#4299E1',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: 'bold',
  },
});