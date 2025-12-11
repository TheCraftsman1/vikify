import React from 'react';

/**
 * Error Boundary component to catch and display React errors gracefully
 * Prevents entire app from crashing on component errors
 */
class ErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = { hasError: false, error: null, errorInfo: null };
    }

    static getDerivedStateFromError(error) {
        return { hasError: true };
    }

    componentDidCatch(error, errorInfo) {
        this.setState({ error, errorInfo });
        console.error('[ErrorBoundary] Caught error:', error, errorInfo);
    }

    handleReset = () => {
        this.setState({ hasError: false, error: null, errorInfo: null });
    };

    render() {
        if (this.state.hasError) {
            return (
                <div style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    minHeight: '100vh',
                    backgroundColor: '#121212',
                    color: '#fff',
                    padding: '24px',
                    textAlign: 'center'
                }}>
                    <div style={{
                        fontSize: '64px',
                        marginBottom: '24px'
                    }}>🎵</div>
                    <h1 style={{
                        fontSize: '32px',
                        fontWeight: 700,
                        marginBottom: '16px'
                    }}>Something went wrong</h1>
                    <p style={{
                        color: '#b3b3b3',
                        marginBottom: '24px',
                        maxWidth: '400px'
                    }}>
                        An error occurred while loading this page. Please try refreshing.
                    </p>
                    <button
                        onClick={this.handleReset}
                        style={{
                            padding: '14px 32px',
                            backgroundColor: '#1db954',
                            color: '#000',
                            borderRadius: '500px',
                            fontWeight: 700,
                            fontSize: '14px',
                            cursor: 'pointer',
                            transition: 'all 0.2s'
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.transform = 'scale(1.04)'}
                        onMouseLeave={(e) => e.currentTarget.style.transform = 'scale(1)'}
                    >
                        Try Again
                    </button>
                    {process.env.NODE_ENV === 'development' && this.state.error && (
                        <details style={{
                            marginTop: '32px',
                            padding: '16px',
                            backgroundColor: 'rgba(255,255,255,0.05)',
                            borderRadius: '8px',
                            textAlign: 'left',
                            maxWidth: '600px',
                            width: '100%'
                        }}>
                            <summary style={{ cursor: 'pointer', color: '#b3b3b3' }}>
                                Error Details (Development)
                            </summary>
                            <pre style={{
                                marginTop: '12px',
                                fontSize: '12px',
                                overflow: 'auto',
                                color: '#f15e6c'
                            }}>
                                {this.state.error.toString()}
                            </pre>
                            <pre style={{
                                marginTop: '8px',
                                fontSize: '11px',
                                overflow: 'auto',
                                color: '#888'
                            }}>
                                {this.state.errorInfo?.componentStack}
                            </pre>
                        </details>
                    )}
                </div>
            );
        }

        return this.props.children;
    }
}

export default ErrorBoundary;
