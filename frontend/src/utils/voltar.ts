// "Voltar" seguro: se a tela foi aberta por link direto ou depois de recarregar a
// página, não existe tela anterior; então leva para um destino fixo em vez de travar.
export function voltar(navigation: any, destino: string = 'Home') {
  if (navigation?.canGoBack?.()) {
    navigation.goBack();
    return;
  }
  navigation?.reset?.({ index: 0, routes: [{ name: destino }] });
}
