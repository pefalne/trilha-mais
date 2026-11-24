import React, { useCallback, useState } from 'react';
import { useNavigation, useFocusEffect } from '@react-navigation/native';
import { usersApi, authApi, UserResponse } from '../services/api';
import { avisar, confirmar } from '../utils/alerta';
import ChaveRecuperacao, { abrirPaginaDoSite, baixarArquivo } from '../components/ChaveRecuperacao';

import {
  View,
  Text,
  TextInput,
  Modal,
  KeyboardAvoidingView,
  Platform,
  StyleSheet,
  SafeAreaView,
  ScrollView,
  TouchableOpacity,
  StatusBar,
} from 'react-native';
import BarraNavegacao, { ChaveAba } from '../components/BarraNavegacao';

const opcoesAvatar = ['🎓', '📚', '🚀', '🧠', '⭐', '🦉', '💡', '🔥'];

export default function Perfil() {
  const navigation = useNavigation<any>();
  const [usuario, setUsuario] = useState<UserResponse | null>(null);
  const [notificacoes, setNotificacoes] = useState<boolean>(true);

  // Formulário de configurações (dados do perfil e troca de senha)
  const [modalVisivel, setModalVisivel] = useState(false);
  const [nome, setNome] = useState('');
  const [avatar, setAvatar] = useState('🎓');
  const [senhaAtual, setSenhaAtual] = useState('');
  const [novaSenha, setNovaSenha] = useState('');
  const [erro, setErro] = useState('');
  const [salvando, setSalvando] = useState(false);

  useFocusEffect(
    useCallback(() => {
      usersApi.me().then(setUsuario).catch(() => {});
      usersApi
        .settings()
        .then((config) => setNotificacoes(config.notificationsEnabled ?? true))
        .catch(() => {});
    }, [])
  );

  // Chave de recuperação, LGPD (baixar dados / excluir conta)
  const [chaveNova, setChaveNova] = useState<string | null>(null);
  const [excluirVisivel, setExcluirVisivel] = useState(false);
  const [senhaExcluir, setSenhaExcluir] = useState('');
  const [erroExcluir, setErroExcluir] = useState('');
  const [processando, setProcessando] = useState(false);

  const gerarChave = async () => {
    const ok = await confirmar(
      'Chave de recuperação',
      'Vamos gerar uma chave nova. A chave anterior deixa de funcionar. Continuar?',
      'Gerar chave',
    );
    if (!ok) return;
    try {
      const r = await usersApi.novaChaveRecuperacao();
      setChaveNova(r.recoveryKey);
    } catch (e: any) {
      avisar('Não foi possível gerar a chave', e?.message);
    }
  };

  const baixarDados = async () => {
    try {
      const dados = await usersApi.exportarDados();
      const ok = baixarArquivo('trilha-mais-meus-dados.json', JSON.stringify(dados, null, 2), 'application/json');
      if (!ok) avisar('Meus dados', 'Abra o Trilha+ no navegador para baixar o arquivo com seus dados.');
    } catch (e: any) {
      avisar('Não foi possível baixar seus dados', e?.message);
    }
  };

  const excluirConta = async () => {
    if (!senhaExcluir) {
      setErroExcluir('Digite sua senha para confirmar.');
      return;
    }
    try {
      setProcessando(true);
      setErroExcluir('');
      await usersApi.excluirConta(senhaExcluir);
      await authApi.logout();
      setExcluirVisivel(false);
      avisar('Conta excluída', 'Sua conta e todos os seus dados foram apagados.');
      navigation.reset({ index: 0, routes: [{ name: 'Login' }] });
    } catch (e: any) {
      setErroExcluir(e?.message ?? 'Não foi possível excluir a conta.');
    } finally {
      setProcessando(false);
    }
  };

  const sair = async () => {
    const confirmado = await confirmar('Sair', 'Deseja sair da sua conta?', 'Sair');
    if (!confirmado) return;
    await authApi.logout();
    navigation.reset({ index: 0, routes: [{ name: 'Login' }] });
  };

  const abrirConfiguracoes = () => {
    setNome(usuario?.name ?? '');
    setAvatar(usuario?.avatarEmoji ?? '🎓');
    setSenhaAtual('');
    setNovaSenha('');
    setErro('');
    setModalVisivel(true);
  };

  const salvarConfiguracoes = async () => {
    if (salvando) return;

    const nomeLimpo = nome.trim();
    if (!nomeLimpo) {
      setErro('Digite o seu nome.');
      return;
    }
    const querTrocarSenha = Boolean(senhaAtual || novaSenha);
    if (querTrocarSenha && (!senhaAtual || novaSenha.length < 6)) {
      setErro('Para trocar a senha, informe a senha atual e uma nova com no mínimo 6 caracteres.');
      return;
    }

    try {
      setSalvando(true);
      setErro('');
      const atualizado = await usersApi.updateProfile({ name: nomeLimpo, avatarEmoji: avatar });
      setUsuario(atualizado);
      if (querTrocarSenha) {
        await usersApi.changePassword(senhaAtual, novaSenha);
      }
      setModalVisivel(false);
      avisar('Pronto', querTrocarSenha ? 'Perfil e senha atualizados.' : 'Perfil atualizado.');
    } catch (e: any) {
      if (e?.status !== 401) setErro(e?.message ?? 'Não foi possível salvar.');
    } finally {
      setSalvando(false);
    }
  };

  const alternarNotificacoes = async () => {
    const novoValor = !notificacoes;
    setNotificacoes(novoValor);
    try {
      await usersApi.updateSettings({ notificationsEnabled: novoValor });
    } catch (e: any) {
      setNotificacoes(!novoValor);
      if (e?.status !== 401) avisar('Erro', e?.message ?? 'Não foi possível salvar a preferência.');
    }
  };

  const aoMudarAba = (novaAba: ChaveAba) => {
    if (novaAba === 'inicio') navigation.navigate('Home');
    if (novaAba === 'agenda') navigation.navigate('Agenda');
    if (novaAba === 'materias') navigation.navigate('Materias');
    if (novaAba === 'progresso') navigation.navigate('Progresso');
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor="#2F68E9" />
      
      <ScrollView
        style={styles.container}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={styles.scrollContent}
      >
        {/* Cabeçalho Azul */}
        <View style={styles.header}>
          {/* Bolha decorativa de fundo no canto superior direito */}
          <View style={styles.bolhaDecorativa} />

          {/* Botão de Voltar */}
          <TouchableOpacity
            style={styles.backButton}
            onPress={() => navigation.goBack()}
            activeOpacity={0.7}
          >
            <Text style={styles.backButtonText}>‹</Text>
          </TouchableOpacity>

          {/* Avatar do Usuário */}
          <View style={styles.avatarBorder}>
            <View style={styles.avatarBackground}>
              <Text style={styles.avatarEmoji}>{usuario?.avatarEmoji ?? '🎓'}</Text>
            </View>
          </View>

          {/* Informações do Usuário */}
          <Text style={styles.userName}>{usuario?.name ?? ''}</Text>
          <Text style={styles.userEmail}>{usuario?.email ?? ''}</Text>
        </View>

        {/* Card Flutuante de Estatísticas */}
        <View style={styles.statsCard}>
          <View style={styles.statItem}>
            <Text style={styles.statValue}>{usuario?.streakDays ?? 0} 🔥</Text>
            <Text style={styles.statLabel}>Dia de estudo</Text>
          </View>
          
          <View style={styles.statItem}>
            <Text style={styles.statValue}>{usuario?.evaluationsCount ?? 0}</Text>
            <Text style={styles.statLabel}>Avaliações</Text>
          </View>
          
          <View style={styles.statItem}>
            <Text style={styles.statValue}>{usuario?.subjectsCount ?? 0}</Text>
            <Text style={styles.statLabel}>Matérias</Text>
          </View>
        </View>

        {/* Menu de Opções */}
        <View style={styles.menuContainer}>
          <TouchableOpacity style={styles.menuItem} activeOpacity={0.7} onPress={abrirConfiguracoes}>
            <Text style={styles.menuIcon}>⚙️</Text>
            <Text style={styles.menuText}>Configurações</Text>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.menuItem} activeOpacity={0.7} onPress={alternarNotificacoes}>
            <Text style={styles.menuIcon}>🔔</Text>
            <Text style={styles.menuText}>Notificações</Text>
            <Text style={styles.valorMenu}>{notificacoes ? 'Ativadas' : 'Desativadas'}</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.menuItem}
            activeOpacity={0.7}
            onPress={() => avisar('Tema', 'Por enquanto o Trilha+ tem só o tema claro.')}
          >
            <Text style={styles.menuIcon}>🎨</Text>
            <Text style={styles.menuText}>Tema</Text>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.menuItem}
            activeOpacity={0.7}
            onPress={() => navigation.navigate('ChatIA')}
          >
            <Text style={styles.menuIcon}>❓</Text>
            <Text style={styles.menuText}>Ajuda e suporte</Text>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.menuItem} activeOpacity={0.7} onPress={gerarChave}>
            <Text style={styles.menuIcon}>🔑</Text>
            <Text style={styles.menuText}>Chave de recuperação</Text>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.menuItem} activeOpacity={0.7} onPress={baixarDados}>
            <Text style={styles.menuIcon}>📥</Text>
            <Text style={styles.menuText}>Baixar meus dados</Text>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.menuItem} activeOpacity={0.7} onPress={() => abrirPaginaDoSite('/termos')}>
            <Text style={styles.menuIcon}>📄</Text>
            <Text style={styles.menuText}>Termos de Uso</Text>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.menuItem} activeOpacity={0.7} onPress={() => abrirPaginaDoSite('/privacidade')}>
            <Text style={styles.menuIcon}>🔒</Text>
            <Text style={styles.menuText}>Política de Privacidade</Text>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.menuItem}
            activeOpacity={0.7}
            onPress={() => { setSenhaExcluir(''); setErroExcluir(''); setExcluirVisivel(true); }}
          >
            <Text style={styles.menuIcon}>🗑️</Text>
            <Text style={[styles.menuText, styles.textDestructive]}>Excluir minha conta</Text>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>

          <TouchableOpacity 
            style={styles.menuItem} 
            activeOpacity={0.7}
            onPress={sair}
          >
            <Text style={styles.menuIcon}>🚪</Text>
            <Text style={[styles.menuText, styles.textDestructive]}>Sair</Text>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>

      <BarraNavegacao ativa="perfil" aoMudar={aoMudarAba} />

      <Modal
        visible={modalVisivel}
        transparent
        animationType="fade"
        onRequestClose={() => setModalVisivel(false)}
      >
        <KeyboardAvoidingView
          style={styles.fundoModal}
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        >
          <View style={styles.conteudoModal}>
            <Text style={styles.tituloModal}>Configurações</Text>

            <Text style={styles.rotuloCampo}>Nome</Text>
            <TextInput
              style={styles.campo}
              value={nome}
              onChangeText={(texto) => {
                setNome(texto);
                setErro('');
              }}
              placeholder="Seu nome"
              placeholderTextColor="#9CA3AF"
              maxLength={80}
              accessibilityLabel="Nome"
            />

            <Text style={styles.rotuloCampo}>Avatar</Text>
            <View style={styles.linhaAvatares}>
              {opcoesAvatar.map((opcao) => (
                <TouchableOpacity
                  key={opcao}
                  style={[styles.opcaoAvatar, opcao === avatar && styles.opcaoAvatarSelecionada]}
                  onPress={() => setAvatar(opcao)}
                  accessibilityLabel={`Avatar ${opcao}`}
                >
                  <Text style={{ fontSize: 20 }}>{opcao}</Text>
                </TouchableOpacity>
              ))}
            </View>

            <Text style={styles.rotuloCampo}>Trocar senha (opcional)</Text>
            <TextInput
              style={styles.campo}
              value={senhaAtual}
              onChangeText={(texto) => {
                setSenhaAtual(texto);
                setErro('');
              }}
              placeholder="Senha atual"
              placeholderTextColor="#9CA3AF"
              secureTextEntry
              accessibilityLabel="Senha atual"
            />
            <TextInput
              style={[styles.campo, { marginTop: 8 }]}
              value={novaSenha}
              onChangeText={(texto) => {
                setNovaSenha(texto);
                setErro('');
              }}
              placeholder="Nova senha"
              placeholderTextColor="#9CA3AF"
              secureTextEntry
              accessibilityLabel="Nova senha"
            />

            {erro ? <Text style={styles.textoErro}>{erro}</Text> : null}

            <View style={styles.acoesModal}>
              <TouchableOpacity style={styles.botaoCancelar} onPress={() => setModalVisivel(false)}>
                <Text style={styles.textoCancelar}>Cancelar</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.botaoSalvar, salvando && { opacity: 0.7 }]}
                onPress={salvarConfiguracoes}
                disabled={salvando}
              >
                <Text style={styles.textoSalvar}>{salvando ? 'Salvando...' : 'Salvar'}</Text>
              </TouchableOpacity>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>

      <Modal visible={Boolean(chaveNova)} transparent animationType="fade" onRequestClose={() => setChaveNova(null)}>
        <View style={styles.fundoModal}>
          <View style={{ width: '100%', maxWidth: 440, alignSelf: 'center' }}>
            {chaveNova ? (
              <ChaveRecuperacao
                chave={chaveNova}
                email={usuario?.email}
                titulo="Sua nova chave de recuperação"
                textoBotao="Pronto"
                onConcluir={() => setChaveNova(null)}
              />
            ) : null}
          </View>
        </View>
      </Modal>

      <Modal visible={excluirVisivel} transparent animationType="fade" onRequestClose={() => setExcluirVisivel(false)}>
        <KeyboardAvoidingView style={styles.fundoModal} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
          <View style={styles.conteudoModal}>
            <Text style={styles.tituloModal}>Excluir minha conta</Text>
            <Text style={{ color: '#4A5568', fontSize: 14, lineHeight: 20, marginBottom: 14 }}>
              Isso apaga para sempre sua conta, matérias, agenda, simulados, desempenho e conversas com a IA.
              Se quiser, baixe seus dados antes. Para confirmar, digite sua senha.
            </Text>
            <TextInput
              style={styles.campo}
              value={senhaExcluir}
              onChangeText={(t) => { setSenhaExcluir(t); setErroExcluir(''); }}
              placeholder="Sua senha"
              placeholderTextColor="#9CA3AF"
              secureTextEntry
              accessibilityLabel="Senha para confirmar a exclusão"
            />
            {erroExcluir ? <Text style={styles.textoErro}>{erroExcluir}</Text> : null}
            <View style={styles.acoesModal}>
              <TouchableOpacity style={styles.botaoCancelar} onPress={() => setExcluirVisivel(false)}>
                <Text style={styles.textoCancelar}>Cancelar</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.botaoSalvar, { backgroundColor: '#DC2626' }, processando && { opacity: 0.7 }]}
                onPress={excluirConta}
                disabled={processando}
              >
                <Text style={styles.textoSalvar}>{processando ? 'Excluindo...' : 'Excluir'}</Text>
              </TouchableOpacity>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  valorMenu: {
    color: '#6B7280',
    fontSize: 13,
    fontWeight: '600',
  },
  fundoModal: {
    flex: 1,
    backgroundColor: 'rgba(15, 23, 42, 0.45)',
    justifyContent: 'center',
    padding: 24,
  },
  conteudoModal: {
    width: '100%',
    maxWidth: 420,
    alignSelf: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 20,
    padding: 24,
  },
  tituloModal: {
    color: '#151A2D',
    fontSize: 18,
    fontWeight: '700',
  },
  rotuloCampo: {
    color: '#151A2D',
    fontSize: 13,
    fontWeight: '600',
    marginTop: 16,
    marginBottom: 8,
  },
  campo: {
    borderWidth: 1,
    borderColor: '#E4E7EC',
    borderRadius: 8,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 14,
    color: '#151A2D',
    backgroundColor: '#F5F6F9',
  },
  linhaAvatares: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  opcaoAvatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#E4E7EC',
    alignItems: 'center',
    justifyContent: 'center',
  },
  opcaoAvatarSelecionada: {
    borderColor: '#2F68E9',
    borderWidth: 2,
    backgroundColor: '#EFF6FF',
  },
  textoErro: {
    color: '#DC2626',
    fontSize: 13,
    fontWeight: '600',
    marginTop: 16,
  },
  acoesModal: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 24,
  },
  botaoCancelar: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#E4E7EC',
    borderRadius: 8,
    paddingVertical: 12,
    alignItems: 'center',
  },
  textoCancelar: {
    color: '#6B7280',
    fontWeight: '700',
  },
  botaoSalvar: {
    flex: 1,
    backgroundColor: '#2F68E9',
    borderRadius: 8,
    paddingVertical: 12,
    alignItems: 'center',
  },
  textoSalvar: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
  safeArea: {
    flex: 1,
    backgroundColor: '#2F68E9', 
  },
  container: {
    flex: 1,
    backgroundColor: '#F8FAFC', 
  },
  scrollContent: {
    flexGrow: 1,
    paddingBottom: 40,
  },
  header: {
    backgroundColor: '#2F68E9',
    alignItems: 'center',
    paddingTop: 16,
    paddingBottom: 70, // Espaço extra para o card flutuante sobrepor
    position: 'relative',
    overflow: 'hidden',
    borderBottomLeftRadius: 24,
    borderBottomRightRadius: 24,
  },
  bolhaDecorativa: {
    position: 'absolute',
    top: -30,
    right: -40,
    width: 180,
    height: 180,
    borderRadius: 90,
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
  },
  backButton: {
    position: 'absolute',
    top: 16,
    left: 20,
    width: 40,
    height: 40,
    borderRadius: 12,
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 10,
  },
  backButtonText: {
    color: '#FFFFFF',
    fontSize: 28,
    fontWeight: 'bold',
    marginTop: -4,
  },
  avatarBorder: {
    width: 96,
    height: 96,
    borderRadius: 48,
    borderWidth: 2,
    borderColor: 'rgba(255, 255, 255, 0.4)',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 20,
    marginBottom: 16,
  },
  avatarBackground: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: '#3B76F6', 
    justifyContent: 'center',
    alignItems: 'center',
  },
  avatarEmoji: {
    fontSize: 40,
  },
  userName: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#FFFFFF',
    marginBottom: 4,
  },
  userEmail: {
    fontSize: 14,
    color: 'rgba(255, 255, 255, 0.8)',
  },
  statsCard: {
    backgroundColor: '#FFFFFF',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginHorizontal: 24,
    paddingVertical: 20,
    paddingHorizontal: 16,
    borderRadius: 20,
    marginTop: -40, // Isso faz o card subir e sobrepor a área azul
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.05,
    shadowRadius: 10,
    elevation: 4, 
    zIndex: 10,
  },
  statItem: {
    flex: 1,
    alignItems: 'center',
  },
  statValue: {
    fontSize: 20,
    fontWeight: '800',
    color: '#0F172A',
    marginBottom: 4,
  },
  statLabel: {
    fontSize: 12,
    color: '#64748B',
    fontWeight: '500',
  },
  menuContainer: {
    paddingHorizontal: 24,
    marginTop: 24,
  },
  menuItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    paddingVertical: 18,
    paddingHorizontal: 20,
    borderRadius: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.03,
    shadowRadius: 4,
    elevation: 2, 
  },
  menuIcon: {
    fontSize: 20,
    marginRight: 16,
    width: 28, 
    textAlign: 'center',
  },
  menuText: {
    flex: 1,
    fontSize: 16,
    fontWeight: '600',
    color: '#475569',
  },
  textDestructive: {
    color: '#EF4444', 
  },
  chevron: {
    fontSize: 22,
    color: '#CBD5E1',
    fontWeight: '300',
  },
});