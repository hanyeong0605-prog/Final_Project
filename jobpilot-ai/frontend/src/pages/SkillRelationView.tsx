import React from 'react';
import { WordCloudSection } from '../features/word-cloud/components/WordCloudSection';

export const SkillRelationView: React.FC = () => {
  return (
    <div className="page-container" style={{ padding: '40px 20px' }}>
      <WordCloudSection />
    </div>
  );
};