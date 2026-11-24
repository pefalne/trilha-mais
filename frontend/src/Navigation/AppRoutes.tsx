import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Platform, StyleSheet, View } from 'react-native';
import { NavigationContainer, createNavigationContainerRef, getPathFromState, LinkingOptions } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import Login from "../pages/TelaLogin";
import Home from "../pages/Home";
import Agenda from "../pages/Agenda";
import Materias from "../pages/Materias";
import Materia from "../pages/Materia";
import Assuntos from "../pages/Assuntos";
import Perfil from "../pages/Perfil";
import Progresso from "../pages/Progresso";
import Cadastro from "../pages/CadastroScreen";
import RecuperacaoSenha from "../pages/RecuperacaoSenha";
import ChatIAScreen from '../pages/ChatIAScreen';
import QuizScreen from '../pages/TelaQuiz';
import ResultadoScreen from '../pages/TelaResultado';

import { authApi, definirAoExpirarSessao, usersApi } from '../services/api';
import useDesktop from '../utils/useDesktop';
import MenuLateral from '../components/desktop/MenuLateral';
import PainelMarca from '../components/desktop/PainelMarca';

import { voltar } from '../utils/voltar';
// Telas de entrada (sem menu); no computador elas ficam ao lado do painel da marca
const ROTAS_ENTRADA = ['Login', 'Cadastro', 'RecuperacaoSenha'];
// Telas que dependem de dados passados pela tela anterior: se a página for
// recarregada nelas, os dados somem e voltamos para o Início.
const PRECISA_PARAMS = ['Materia', 'Assuntos', 'Quiz', 'Resultado'];

// Endereços do app no navegador. A landing page fica em "/" e o app em "/app".
const linking: LinkingOptions<any> = {
  prefixes: ['https://trilha-mais.vercel.app', 'trilhamais://'],
  config: {
    screens: {
      Login: 'app/entrar',
      Cadastro: 'app/cadastro',
      RecuperacaoSenha: 'app/recuperar-senha',
      Home: 'app',
      Agenda: 'app/agenda',
      Materias: 'app/materias',
      Materia: 'app/materia',
      Assuntos: 'app/assuntos',
      Perfil: 'app/perfil',
      Progresso: 'app/progresso',
      ChatIAScreen: 'app/assistente',
      ChatIA: 'app/assistente-ia',
      Chat: 'app/chat',
      Quiz: 'app/simulado',
      Resultado: 'app/resultado',
    },
  },
  // Os parâmetros (objetos do simulado, ids) não vão para a barra de endereço
  getPathFromState: (state, options) => getPathFromState(state, options).split('?')[0],
};

const TITULOS: Record<string, string> = {
  Login: 'Entrar', Cadastro: 'Criar conta', RecuperacaoSenha: 'Recuperar senha', Home: 'Início',
  Agenda: 'Agenda', Materias: 'Matérias', Materia: 'Matéria', Assuntos: 'Assuntos', Perfil: 'Perfil',
  Progresso: 'Progresso', ChatIAScreen: 'Assistente IA', ChatIA: 'Assistente IA', Chat: 'Assistente IA',
  Quiz: 'Simulado', Resultado: 'Resultado',
};

const Stack = createNativeStackNavigator();
const navegacaoRef = createNavigationContainerRef<any>();

export default function AppRoutes() {
  const [rotaInicial, setRotaInicial] = useState<'Login' | 'Home' | null>(null);
  const [rotaAtual, setRotaAtual] = useState<string | null>(null);
  const desktop = useDesktop();
  const atualizarRota = () => setRotaAtual(navegacaoRef.getCurrentRoute()?.name ?? null);

  // Proteção das rotas: sem login, só as telas de entrada; tela que perdeu os
  // dados ao recarregar volta para o Início.
  const protegerRota = () => {
    const atual = navegacaoRef.getCurrentRoute();
    if (!atual) return;
    const logado = rotaInicial === 'Home';
    if (!logado && !ROTAS_ENTRADA.includes(atual.name)) {
      navegacaoRef.reset({ index: 0, routes: [{ name: 'Login' }] });
    } else if (logado && PRECISA_PARAMS.includes(atual.name) && !atual.params) {
      navegacaoRef.reset({ index: 0, routes: [{ name: 'Home' }] });
    }
  };
  const aoFicarPronto = () => {
    protegerRota();
    atualizarRota();
  };

  useEffect(() => {
    // Sessão expirada em qualquer tela: volta para o login
    definirAoExpirarSessao(() => {
      if (navegacaoRef.isReady()) {
        navegacaoRef.reset({ index: 0, routes: [{ name: 'Login' }] });
      }
    });

    // Se já existe um token válido salvo, entra direto
    (async () => {
      if (!(await authApi.temSessao())) {
        setRotaInicial('Login');
        return;
      }
      // Confere a sessão, mas sem prender a abertura: se o servidor estiver
      // acordando (plano gratuito), entra mesmo assim e as telas carregam depois.
      // Só volta para o login se o servidor disser que a sessão não vale mais.
      const tempoEsgotado = new Promise<'demorou'>((ok) => setTimeout(() => ok('demorou'), 6000));
      try {
        const verificacao = usersApi.me();
        verificacao.catch(() => {}); // se responder depois do prazo, o 401 já é tratado no api.ts
        await Promise.race([verificacao, tempoEsgotado]);
        setRotaInicial('Home');
      } catch (e: any) {
        if (e?.status === 401 || e?.status === 403 || e?.status === 404) {
          await authApi.logout();
          setRotaInicial('Login');
        } else {
          setRotaInicial('Home');
        }
      }
    })();

    return () => definirAoExpirarSessao(null);
  }, []);

  useEffect(() => {
    // Web: o native-stack esconde a tela anterior com aria-hidden enquanto o botão
    // clicado nela ainda tem o foco, e o navegador avisa "Blocked aria-hidden".
    // Por isso tiramos o foco do elemento ativo antes de a navegação trocar de tela.
    if (Platform.OS !== 'web') return;

    const tirarFoco = () => {
      const ativo = document.activeElement;
      if (ativo instanceof HTMLElement) ativo.blur();
    };

    // O ref guarda o listener e o liga ao NavigationContainer quando ele monta
    navegacaoRef.addListener('__unsafe_action__', tirarFoco);
    return () => navegacaoRef.removeListener('__unsafe_action__', tirarFoco);
  }, []);

  if (!rotaInicial) {
    return (
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#FFFFFF' }}>
        <ActivityIndicator size="large" color="#2563EB" />
      </View>
    );
  }

  const rota = rotaAtual ?? rotaInicial;
  const entrada = ROTAS_ENTRADA.includes(rota);

  // A mesma árvore é usada no celular e no computador (só mudam os estilos e o lado
  // esquerdo), para o NavigationContainer não ser recriado ao redimensionar a janela.
  return (
    <View style={desktop ? estilos.pagina : estilos.cheio}>
      {desktop ? (
        entrada ? (
          <PainelMarca />
        ) : (
          <MenuLateral rotaAtual={rota} aoIr={(nome) => navegacaoRef.isReady() && navegacaoRef.navigate(nome)} />
        )
      ) : null}
      <View style={desktop ? (entrada ? estilos.ladoEntrada : estilos.area) : estilos.cheio}>
        <View style={desktop && !entrada ? estilos.conteudo : estilos.cheio}>
    <NavigationContainer
      ref={navegacaoRef}
      linking={linking}
      documentTitle={{ formatter: (_opcoes, r) => `${TITULOS[r?.name ?? ''] ?? 'Trilha+'} · Trilha+` }}
      onReady={aoFicarPronto}
      onStateChange={atualizarRota}
    >
      <Stack.Navigator initialRouteName={rotaInicial} screenOptions={{ headerShown: false }}>
        <Stack.Screen name="Login" component={Login} />
        <Stack.Screen name="Cadastro" component={Cadastro} />
        <Stack.Screen name="RecuperacaoSenha" component={RecuperacaoSenha} />
        <Stack.Screen name="Home" component={Home} />
        <Stack.Screen name="Agenda" component={Agenda} />
        <Stack.Screen name="Materias" component={Materias} />
        <Stack.Screen name="Materia" component={Materia} />
        <Stack.Screen name="Assuntos" component={Assuntos} />
        <Stack.Screen name="Perfil" component={Perfil} />
        <Stack.Screen name="Progresso" component={Progresso} />

        {/* Rotas de Chat com IA: suporta ChatIAScreen, ChatIA e Chat para evitar erros de navegação */}
        <Stack.Screen name="ChatIAScreen">
          {({ navigation }) => <ChatIAScreen onBack={() => voltar(navigation)} />}
        </Stack.Screen>
        <Stack.Screen name="ChatIA">
          {({ navigation }) => <ChatIAScreen onBack={() => voltar(navigation)} />}
        </Stack.Screen>
        <Stack.Screen name="Chat">
          {({ navigation }) => <ChatIAScreen onBack={() => voltar(navigation)} />}
        </Stack.Screen>

        {/* Tela de Quiz interativo */}
        <Stack.Screen name="Quiz">
          {({ route, navigation }) => {
            const p = (route.params as any) ?? {};
            return (
              <QuizScreen
                topicId={p.topicId}
                subjectId={p.subjectId}
                questionCount={p.totalQuestoes}
                onClose={() => voltar(navigation)}
                onFinish={(resultado) =>
                  navigation.replace('Resultado', { resultado, quizParams: p })
                }
              />
            );
          }}
        </Stack.Screen>

        {/* Tela de Resultado com revisão e pontos fortes/fracos */}
        <Stack.Screen name="Resultado">
          {({ route, navigation }) => (
            <ResultadoScreen
              resultado={(route.params as any)?.resultado}
              onRefazer={() => navigation.replace('Quiz', (route.params as any)?.quizParams)}
              onDesempenho={() => navigation.navigate('Progresso')}
              onVoltar={() => navigation.navigate('Home')}
            />
          )}
        </Stack.Screen>
      </Stack.Navigator>
    </NavigationContainer>
        </View>
      </View>
    </View>
  );
}

const estilos = StyleSheet.create({
  cheio: { flex: 1 },
  pagina: { flex: 1, flexDirection: 'row', backgroundColor: '#EEF1F8' },
  area: { flex: 1, alignItems: 'center', paddingHorizontal: 32, paddingVertical: 24 },
  conteudo: {
    flex: 1, width: '100%', maxWidth: 1120, borderRadius: 24, overflow: 'hidden',
    backgroundColor: '#FFFFFF', borderWidth: 1, borderColor: '#E4E7EC',
  },
  ladoEntrada: { width: 560, backgroundColor: '#FFFFFF' },
});