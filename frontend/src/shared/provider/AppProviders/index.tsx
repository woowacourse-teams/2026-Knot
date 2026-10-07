import { DialogProvider } from "@provider/context/dialogContext";
import { ToastProvider } from "@provider/context/toastContext";
import type { ReactNode } from "react";
import { matchRoutes, useLocation, type RouteObject } from "react-router";

interface AppProvidersProps {
  children: ReactNode;
  routes: RouteObject[];
}

/** 라우트의 독 유무를 토스트 정책에 연결하고, 앱 전체의 토스트와 모달을 제공해요. */
export default function AppProviders({ children, routes }: AppProvidersProps) {
  const location = useLocation();
  const hasDock =
    matchRoutes(routes, location)?.some(
      ({ route }) => route.handle?.hasDock === true,
    ) ?? false;

  return (
    <ToastProvider surface={hasDock ? "docked" : "floating"}>
      <DialogProvider>{children}</DialogProvider>
    </ToastProvider>
  );
}
