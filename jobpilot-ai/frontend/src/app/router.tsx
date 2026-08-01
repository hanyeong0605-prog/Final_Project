import { createBrowserRouter } from "react-router-dom";
import { AppShell } from "../layouts/AppShell";
import { DashboardPage } from "../pages/DashboardPage";
import { JobMatchesPage } from "../pages/JobMatchesPage";
import { OpportunitiesPage } from "../pages/OpportunitiesPage";
import { PlannerPage } from "../pages/PlannerPage";
import { ProfilePage } from "../pages/ProfilePage";
import { RepositoryAnalysisPage } from "../pages/RepositoryAnalysisPage";
import { AllJobPostingsPage } from "../pages/AllJobPostingsPage";
import { LoginPage } from "../pages/LoginPage";
import { SignupPage } from "../pages/SignupPage";
import { MyPage } from "../pages/MyPage";
import { RequireAuth } from "../features/auth/components/RequireAuth";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/signup", element: <SignupPage /> },
  {
    path: "/",
    element: <RequireAuth><AppShell /></RequireAuth>,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: "jobs", element: <JobMatchesPage /> },
      { path: "job-postings", element: <AllJobPostingsPage /> },
      { path: "opportunities", element: <OpportunitiesPage /> },
      { path: "planner", element: <PlannerPage /> },
      { path: "profile", element: <ProfilePage /> },
      { path: "account", element: <MyPage /> },
      { path: "repository-analysis", element: <RepositoryAnalysisPage /> },
    ],
  },
]);
