import { createBrowserRouter } from "react-router-dom";
import { AppShell } from "../layouts/AppShell";
import { DashboardPage } from "../pages/DashboardPage";
import { JobMatchesPage } from "../pages/JobMatchesPage";
import { OpportunitiesPage } from "../pages/OpportunitiesPage";
import { PlannerPage } from "../pages/PlannerPage";
import { ProfilePage } from "../pages/ProfilePage";
import { RepositoryAnalysisPage } from "../pages/RepositoryAnalysisPage";
import { MockInterviewPage } from "../pages/MockInterviewPage";
import { AllJobPostingsPage } from "../pages/AllJobPostingsPage";
import { JobPostingDetailPage } from "../pages/JobPostingDetailPage";
import { LoginPage } from "../pages/LoginPage";
import { SignupPage } from "../pages/SignupPage";
import { MyPage } from "../pages/MyPage";
import { RequireAuth } from "../features/auth/components/RequireAuth";
import { LocationJobsPage } from "../pages/LocationJobsPage";

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
      { path: "job-postings/:id", element: <JobPostingDetailPage /> },
      { path: "locationjobs", element: <LocationJobsPage /> },
      { path: "opportunities", element: <OpportunitiesPage /> },
      { path: "planner", element: <PlannerPage /> },
      { path: "profile", element: <ProfilePage /> },
      { path: "mock-interview", element: <MockInterviewPage /> },
      { path: "account", element: <MyPage /> },
      { path: "repository-analysis", element: <RepositoryAnalysisPage /> },
    ],
  },
]);
