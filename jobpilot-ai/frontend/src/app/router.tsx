import { createBrowserRouter } from "react-router-dom";
import { AppShell } from "../layouts/AppShell";
import { DashboardPage } from "../pages/DashboardPage";
import { JobMatchesPage } from "../pages/JobMatchesPage";
import { OpportunitiesPage } from "../pages/OpportunitiesPage";
import { OpportunityDetailPage } from "../pages/OpportunityDetailPage";
import { PlannerPage } from "../pages/PlannerPage";
import { ProfilePage } from "../pages/ProfilePage";
import { ResumePage } from "../pages/ResumePage";
import { RepositoryAnalysisPage } from "../pages/RepositoryAnalysisPage";
import { MockInterviewPage } from "../pages/MockInterviewPage";
import { TimelinePage } from "../pages/TimelinePage";
import { AllJobPostingsPage } from "../pages/AllJobPostingsPage";
import { JobPostingDetailPage } from "../pages/JobPostingDetailPage";
import { LoginPage } from "../pages/LoginPage";
import { SignupPage } from "../pages/SignupPage";
import { MyPage } from "../pages/MyPage";
import { EmployerLoginPage } from "../pages/EmployerLoginPage";
import { EmployerSignupPage } from "../pages/EmployerSignupPage";
import { EmployerMyPage } from "../pages/EmployerMyPage";
import { RequireAuth } from "../features/auth/components/RequireAuth";
import { RequireAdmin } from "../features/auth/components/RequireAdmin";
import { RequireAdminFaceVerified } from "../features/auth/components/RequireAdminFaceVerified";
import { RequireEmployer } from "../features/employer/components/RequireEmployer";
import { LocationJobsPage } from "../pages/LocationJobsPage";
import { StatisticsDashboard } from "../pages/StatisticsDashboard";
import { QuestionPage } from "../pages/QuestionPage";
import { CareerTestPage } from "../pages/CareerTestPage";
import { SkillRelationView } from "../pages/SkillRelationView";
import { CameraPairPage } from "../pages/CameraPairPage";
import { SubscriptionResultPage } from "../pages/SubscriptionResultPage";
import { HomePage } from "../pages/HomePage";
import { AdminPage } from "../pages/AdminPage";
import { AdminFacePairPage } from "../pages/AdminFacePairPage";
import { AdminFaceReferencePage } from "../pages/AdminFaceReferencePage";
import { OAuthCallbackPage } from "../pages/OAuthCallbackPage";
import { OAuthCompletePage } from "../pages/OAuthCompletePage";
import { CapabilityManagementPage } from "../pages/CapabilityManagementPage";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/signup", element: <SignupPage /> },
  { path: "/employer/login", element: <EmployerLoginPage /> },
  { path: "/employer/signup", element: <EmployerSignupPage /> },
  { path: "/employer", element: <RequireEmployer><EmployerMyPage /></RequireEmployer> },
  { path: "/oauth/callback", element: <OAuthCallbackPage /> },
  { path: "/oauth/complete", element: <OAuthCompletePage /> },
  { path: "/camera-pair", element: <CameraPairPage /> },
  { path: "/admin-face-pair", element: <AdminFacePairPage /> },

  {
    path: "/",
    element: <RequireAuth><AppShell /></RequireAuth>,
    children: [
      { index: true, element: <HomePage /> },
      { path: "dashboard", element: <DashboardPage /> },
      { path: "jobs", element: <JobMatchesPage /> },
      { path: "job-postings", element: <AllJobPostingsPage /> },
      { path: "job-postings/:id", element: <JobPostingDetailPage /> },
      { path: "locationjobs", element: <LocationJobsPage /> },
      { path: "opportunities", element: <OpportunitiesPage /> },
      { path: "opportunities/:id", element: <OpportunityDetailPage /> },
      { path: "planner", element: <PlannerPage /> },
      { path: "capability", element: <CapabilityManagementPage /> },
      { path: "profile", element: <ProfilePage /> },
      { path: "resume", element: <ResumePage /> },
      { path: "mock-interview", element: <MockInterviewPage /> },
      { path: "timeline", element: <TimelinePage /> },
      { path: "account", element: <MyPage /> },
      { path: "repository-analysis", element: <RepositoryAnalysisPage /> },
      { path: "statistics", element: <StatisticsDashboard /> },
      { path: "question", element: <QuestionPage /> },
      { path: "tests/:testKey", element: <CareerTestPage /> },
      { path: "skill-relation", element: <SkillRelationView /> },
      { path: "subscription/success", element: <SubscriptionResultPage /> },
      { path: "subscription/fail", element: <SubscriptionResultPage /> },
      { path: "admin", element: <RequireAdmin><AdminPage /></RequireAdmin> },
      { path: "admin/face-references", element: <RequireAdmin><RequireAdminFaceVerified><AdminFaceReferencePage /></RequireAdminFaceVerified></RequireAdmin> },
    ],
  },
]);
